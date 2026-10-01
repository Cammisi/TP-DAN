# Planificación global — ¿Quién Juega?

Documento de planificación y seguimiento del TP integrador. Es el **único lugar** donde se decide qué se hace y en qué orden.

> **Cómo usar este documento**
> 1. Buscá la primera tarea sin marcar (`- [ ] **T-`) cuyo campo *Depende de* ya esté cumplido.
> 2. Decile a tu IA: *"Leé `docs/planificacion_global.md`, tomá la tarea T-XX y generá `docs/plan_XX_<slug>.md` con el plan de implementación"*.
> 3. Revisás el `plan_*`, lo aprobás, y recién ahí la IA implementa.
> 4. Al terminar, marcás los checkboxes de la tarea y sus requisitos, y hacés commit.
>
> Una tarea **no se marca** hasta cumplir su criterio de *Terminada cuando*.

---

## 1. Estado actual del proyecto

Estado del código backend, actualizado a medida que se implementan tareas.

| Componente | Estado | Detalle |
|---|---|---|
| `eureka-server` | ✅ Hecho | App + `application.yaml` + Dockerfile. Verificado en ejecución (T-02); necesitó agregar `@EnableEurekaServer`. |
| `cloud-gateway` | ✅ Hecho | Rutas hacia los 3 MS, validación del JWT, rol admin en las rutas de admin y CORS (T-40). Verificado con el stack completo el 2026-09-30. |
| `docker-compose.yml` | ✅ Hecho | Postgres, Mongo, Kafka (KRaft), Kafka UI, los 5 servicios Spring, red y volúmenes. Verificado: `docker compose up --build` deja los 9 contenedores `healthy` (T-02). |
| `fan-identity-service` | ✅ Hecho (carril A) | Modelo de datos y catálogo (T-10), auth con JWT (T-11), suscripciones (T-12), suscriptores por equipo (T-13), historial de notificaciones (T-14), catálogo de ligas/equipos (T-15) y endpoints internos blindados con `INTERNAL_API_KEY` (T-41). |
| `match-ingestion-service` | ✅ Hecho (carril B) | Documentos Mongo y semilla (T-20), ingesta de eventos (T-21), consulta de partidos/eventos (T-22) y productor Kafka (T-23). Verificado en Docker el 2026-09-26. |
| `alert-dispatcher-service` | ✅ Hecho (carril B) | Consumer Kafka y orquestación del fan-out (T-30), fallback real que registra no despachados y `GET /api/alerts/status` (T-31). Verificado en Docker el 2026-09-26, incluida la apertura/cierre real del circuito. |
| `frontend-app` | ✅ Hecho (carril C) | Next.js Pages Router: scaffolding (T-60), API/sesión (T-61), login/registro (T-62), catálogo/suscripciones (T-63), centro de alertas (T-64) y panel de admin (T-65). Verificado en Docker el 2026-09-30. Falta el muro de partidos en vivo del mockup (ver T-66, propuesta). |
| Seguridad (JWT) | ✅ Hecho | Emisión y validación del JWT en fan-identity (T-11). Validación en el gateway, rol admin, CORS (T-40) y clave compartida para los endpoints internos (T-41). Verificado con el stack completo el 2026-09-30 (ver `docs/explicacion_seguridad.md`). |
| Mensajería (Kafka) | ✅ Hecho | Productor (`match-ingestion-service`, T-23) y consumer (`alert-dispatcher-service`, T-30) con el esquema de T-01. Verificado contra Kafka real el 2026-09-26 (ver `docs/explicacion_mensajeria_kafka.md`). |
| Logs centralizados | ✅ Hecho | Loki + Promtail (T-51). Verificado el 2026-09-30: logs de los 13 contenedores indexados por `service`/`level`, correlación por `eventId` confirmada entre los tres microservicios. |
| Telemetría de métricas | ✅ Hecho | Actuator + Micrometer → Prometheus → Grafana (T-50). Verificado el 2026-09-30: los 5 servicios `up` en Prometheus, dashboard aprovisionado en Grafana, Actuator bloqueado desde el host vía gateway. |
| Resilience4J (extra) | ✅ Hecho | El fallback ya no descarta eventos: los registra como no despachados y los expone en `GET /api/alerts/status` (T-31). Demo de apertura/cierre del circuito reproducida el 2026-09-26 (ver `docs/explicacion_circuit_breaker.md`). |

### Cobertura de los requisitos obligatorios del enunciado

| Requisito del enunciado | Estado | Se resuelve en |
|---|---|---|
| ≥ 3 microservicios Spring Boot + 1 frontend | ✅ | Fase 1 + T-60 a T-65 (verificados) |
| 1 base SQL + 1 NoSQL, 1 microservicio ≠ 2 bases | ✅ | T-10, T-20 (verificados) |
| Mensajería asincrónica | ✅ | T-23, T-30 (verificados) |
| Spring Cloud Gateway + Eureka | ✅ | T-02 (verificado) |
| Ejecución en contenedores Docker | ✅ | T-02 (verificado) |
| Seguridad | ✅ | T-11, T-40, T-41, T-42 (verificados) |
| Almacén centralizado de logs | ✅ | T-51 (verificado) |
| Telemetría de métricas de rendimiento | ✅ | T-50 (verificado) |
| 1 característica extra (Resilience4J) | ✅ | T-31 (verificado) |

---

## 2. Decisiones de diseño

Huecos que ni el enunciado ni la resolución propuesta resolvían. **Las seis están decididas**: las tareas de la sección 6 ya las reflejan. Si alguna se reabre, hay que revisar las tareas listadas en *Afecta*.

- [x] **D-1 · ¿Quién es dueño del catálogo de ligas y equipos?** → **`fan-identity-service` (PostgreSQL)**
  `League` y `Team` viven en Postgres junto a `Fan` y `Subscription`, con FK real entre suscripción y equipo. Es exactamente la "integridad referencial" con la que el informe justificó elegir SQL.
  **Consecuencias a tener en cuenta:**
  - `match-ingestion-service` no tiene catálogo: guarda partidos y eventos en Mongo referenciando `teamId` como string (referencia débil entre servicios, normal en microservicios).
  - El gateway necesita una ruta nueva `/api/catalog/**` hacia fan-identity; servir el catálogo bajo `/api/fans/**` sería confuso.
  - `match-ingestion` **no puede validar localmente** que el `teamId` de un evento entrante exista. Valida formato y que el partido exista en su propia base; la validación contra el catálogo queda fuera de alcance para no agregar otra llamada entre servicios.
  - La suscripción no necesita `teamName` desnormalizado: se resuelve con un join dentro de fan-identity.
  *Afecta:* T-10, T-12, T-15, T-20, T-21, T-22, T-63.

- [x] **D-2 · ¿Dónde se valida el JWT?** → **en el gateway y en `fan-identity-service`**
  El gateway valida la firma y bloquea lo no autenticado: es la primera línea y evita que tráfico anónimo llegue a los microservicios. `fan-identity-service` valida también, porque necesita saber *qué* fan está haciendo la request para resolver sus suscripciones y notificaciones. `match-ingestion` y `alert-dispatcher` no llevan Spring Security propio: no están expuestos al host y solo se alcanzan por el gateway.
  *Afecta:* T-11, T-40, T-41.

- [x] **D-3 · Persistencia del historial de alertas** → **callback HTTP**
  Después de resolver los suscriptos, `alert-dispatcher-service` hace un `POST` a un endpoint interno de `fan-identity-service` que persiste la notificación. Se mantiene lo que ya estaba decidido en `CLAUDE.md`: Single Source of Truth real y más defendible en el coloquio que compartir la base Postgres entre dos servicios. A cambio, hay que blindar ese endpoint interno (T-41).
  *Afecta:* T-14, T-30, T-41.

- [x] **D-4 · Stack de observabilidad (logs + métricas)** → **Prometheus + Grafana + Loki + Promtail**
  - **Métricas:** Actuator + Micrometer exponen → Prometheus almacena → Grafana muestra.
  - **Logs:** SLF4J/Logback escriben (ya viene con Spring Boot) → Promtail recolecta de los contenedores → Loki almacena → el mismo Grafana muestra.

  Se eligió sobre ELK por dos motivos: el grupo ya vio Prometheus y Grafana en la materia, y Elasticsearch pesa demasiado para correr junto a Postgres, MongoDB, Kafka y cinco JVMs en una notebook.
  **Aclaración conceptual:** SLF4J es la API con la que se *escriben* los logs dentro de la app, no un almacén centralizado; Prometheus guarda números, no texto de log; Grafana no almacena nada, solo visualiza. El requisito de "almacén centralizado de logs" lo cumple **Loki**.
  *Afecta:* T-50, T-51.

- [x] **D-5 · ¿Cómo se materializa el rol Administrador?** → **`ROLE_ADMIN` real en `fan-identity-service`**
  El rol viaja en los claims del JWT y hay un usuario admin sembrado. Cuesta poco y evita tener un panel de administración sin autenticación.
  *Afecta:* T-10, T-11, T-40, T-65.

- [x] **D-6 · ¿Qué sirve la ruta `/api/alerts/**` del gateway?** → **solo el estado del despachador**
  Con D-3, el historial del fan se lee desde `fan-identity-service` (`GET /api/fans/me/notifications`), así que la ruta quedaba huérfana. `alert-dispatcher-service` la usa para exponer últimos eventos procesados y estado del circuito, que es lo que consume el panel de administrador.
  *Afecta:* T-31, T-64, T-65.

---

## 3. Inconsistencias y riesgos detectados

- [x] **R-1 · Stack muy reciente sin evidencia de que arranque:** Spring Boot 4.1 + Java 25 + Spring Cloud 2025.1.2 + `spring-cloud-starter-gateway-server-webmvc`. Resuelto en T-02 (2026-09-17): `docker compose up --build` levanta los 9 contenedores `healthy`. Único ajuste necesario: `@EnableEurekaServer` faltante en `eureka-server`.
- [ ] **R-2 · `frontend-app/` todavía no existe.** Es uno de los componentes que exige el enunciado y quedó al final del plan. Conviene arrancar T-60 en paralelo con la Fase 1 para no dejarlo para la última semana.
- [ ] **R-3 · Los `application.yaml` de los 3 microservicios estaban casi vacíos** (solo `spring.application.name`). Se completaron con defaults locales en T-02 (puerto, Eureka, datasource/Mongo/Kafka apuntando a `localhost`). **Falta verificar** que `mvn spring-boot:run` funcione contra esos defaults: esta máquina no tiene Maven ni JDK 25 instalados, solo se probó vía Docker.
- [x] **R-4 · `fan-identity-service` tiene `spring-boot-starter-security` sin configurar.** Resuelto en T-11: `SecurityConfig` configurado con `JwtAuthenticationFilter`, rutas públicas (`/api/fans/auth/**`), rutas protegidas con JWT y contraseñas hasheadas con BCrypt.
- [x] **R-5 · El path del Feign client era provisorio** (`GET /api/fans/subscribers/{teamId}`). Congelado en T-01, implementado y verificado en T-13: ahora `GET /internal/fans/subscribers/{teamId}`.
- [x] **R-6 · Actuator solo está en `cloud-gateway`**, y los healthchecks de Docker son TCP contra el puerto. Resuelto en T-50: los 5 servicios exponen `/actuator/prometheus` y los healthchecks de `docker-compose.yml` pasan a `curl -f http://localhost:<puerto>/actuator/health`. Verificado el 2026-09-30.
- [x] **R-7 · Nadie valida el rol admin en `POST /api/matches/{matchId}/events`.** Por D-2 el gateway solo valida firma/autenticación (no rol), y `match-ingestion-service` no tiene Spring Security propio porque no está expuesto al host. Con el contrato de T-01, un fan autenticado (no admin) podría llamar a este endpoint vía gateway. Lo mismo aplica a `GET /api/alerts/status` (admin según el contrato, D-6), ya que `alert-dispatcher-service` tampoco tiene Spring Security. Resuelto en T-40 (`JwtValidationFilter` responde `403 FORBIDDEN`). Confirmado con el stack completo el 2026-09-30: un fan autenticado recibe `403 FORBIDDEN` tanto en `POST /api/matches/{matchId}/events` como en `GET /api/alerts/status`; el admin accede sin problema a ambos.
- [x] **R-8 · La URI de Mongo de `match-ingestion-service` se ignora en Boot 4.** `application.yaml` usaba `spring.data.mongodb.uri` y el compose `SPRING_DATA_MONGODB_URI`, pero en Spring Boot 4 la property pasó a `spring.mongodb.uri` (el metadata de `spring-boot-mongodb-4.1.0.jar` marca la vieja como *deprecated* nivel `error`: ya no se bindea). El servicio intentaría conectarse a `localhost:27017` dentro del contenedor. T-02 no lo detectó porque sin repositorios el driver nunca se conecta. Corregido en el código de T-20 (ver [plan](plan_20_modelo_partidos_eventos.md)); confirmado en Docker el 2026-09-26: `match-ingestion-service` conecta contra `mongo:27017` del compose y sirve los partidos semilla.
- [ ] **R-9 · `docker compose up` (sin `--build`) reusa imágenes viejas en silencio.** Detectado el 2026-09-26 al verificar el carril B: tras `git pull` con el commit de T-20 a T-31, `docker compose up -d` levantó los contenedores con las imágenes cacheadas de días antes (ninguna incluía ni el código nuevo de `match-ingestion-service`/`alert-dispatcher-service` ni los fixes de la revisión de `fan-identity-service`). Como el healthcheck es solo TCP (R-6), los contenedores igual quedaban `healthy` corriendo código viejo: `GET /api/matches` daba `404` porque el `MatchController` de esa imagen no existía todavía. Se resolvió con `docker compose up -d --build`. **No hay tarea que lo corrija de raíz** (depende de acordarse de pasar `--build` después de cada `pull`); queda como algo a tener presente en el coloquio y candidato a mencionar en `docs/explicacion_docker_compose.md`.
- [ ] **R-10 · El timeout de Feign de T-31 se ignora en silencio.** `alert-dispatcher-service/application.yaml` lo configura bajo `feign.client.config.fan-identity-service`, pero en Spring Cloud OpenFeign 4+ el prefijo es `spring.cloud.openfeign.client` (verificado en `FeignClientProperties` de `spring-cloud-openfeign-core-5.0.2.jar`). Una property mal escrita no da error: el callback de notificación usa el default de Feign (60 s de lectura) en vez de 3 s. La verificación de T-31 sigue siendo válida (no ejercitaba ese caso), pero la mitigación documentada en `explicacion_circuit_breaker.md` no está activa. Se corrige en T-41 (ver [plan](plan_41_blindar_endpoints_internos.md)), que toca la misma configuración. **Corregido en el código de T-41**: el bloque pasó a `spring.cloud.openfeign.client.config`, y `AlertDispatcherServiceApplicationTests` verifica que el timeout se aplique (en verde; con un override a otro valor falla, así que el test no es vacío).
- [ ] **R-11 · Justo después de un `docker compose up` en frío, el primer evento puede fallar transitoriamente.** Detectado el 2026-09-30 al reverificar Fase 2/3 con Docker Desktop recién arrancado: con los 13 contenedores iniciando a la vez, el consumer de Kafka de `alert-dispatcher-service` perdió el heartbeat con el broker (`session timed out without receiving a heartbeat response`) y, al reconectar, la caché del load balancer hacia `fan-identity-service` todavía no tenía la instancia — el evento quedó en `undispatchedEvents` con el circuito pasando brevemente a medir fallos. No es un bug de T-30/T-31: el fallback se comportó exactamente como está diseñado (registró el evento, no lo perdió) y el sistema se auto-recuperó solo; un segundo evento, ~1 minuto después, se despachó sin problema. **Para el coloquio:** esperar 1-2 minutos después de `docker compose up` antes de disparar la demo del flujo de eventos, o tenerlo presente como ejemplo en vivo de para qué sirve el Circuit Breaker. Ver también R-14: otra causa de demora del primer evento, encontrada el 2026-10-01.
- [ ] **R-12 · La búsqueda del catálogo (`GET /api/catalog/teams?name=`) no ignora tildes.** Detectado el 2026-09-30 armando la colección de T-71: `?name=colon` no encuentra a "Colón" porque la comparación es `LIKE` case-insensitive pero no accent-insensitive — "colon" y "Colón" difieren en la o/ó y no hay substring en común desde ahí. Ningún requisito de T-15/T-63 pide ignorar tildes explícitamente, así que no se lo corrige de oficio (regla de `CLAUDE.md` de no inventar alcance); queda anotado para que el buscador del frontend no sorprenda en la demo: buscar por el prefijo sin la tilde ("col", "uni") sí funciona. Si se quiere corregir de verdad, la opción más simple es la extensión `unaccent` de Postgres en la query de `TeamRepository.search`.
- [x] **R-13 · Un `MATCH_END` ingestado por la API nunca llegaba como alerta.** Detectado el 2026-10-01 en la revisión de código de Fase 4/5: `MatchEventService.buildMatchEndEvent` no seteaba el `matchId`, así que el evento se guardaba sin él (no aparecía en `GET /api/matches/{id}/events`), se publicaba en Kafka con `matchId: null` y `fan-identity-service` rechazaba cada callback con `400`, dejando el evento en `undispatchedEvents`. Los `MATCH_END` semilla no lo mostraban porque `DataSeeder` sí setea el `matchId`, y ni T-42 ni T-71 probaban este tipo. Se volvió alcanzable desde la UI con el "Fin del partido" de `/admin` (T-65). Corregido, con un test en `MatchEventServiceTest` que verifica el `matchId` guardado y el de los dos mensajes publicados.
- [ ] **R-14 · Si el consumer arranca antes que el productor, `match-events` se crea con 1 partición y los primeros eventos tardan hasta 5 minutos.** Detectado el 2026-10-01 verificando R-13 con volúmenes limpios: `alert-dispatcher-service` se suscribió a `match-events` antes de que `match-ingestion-service` lo declarara, y el broker lo auto-creó con su default de 1 partición. Después el `NewTopic` de `KafkaTopicConfig` lo amplió a 3, pero el consumer siguió con solo `match-events-0` asignada hasta refrescar su metadata (`metadata.max.age.ms`, 5 minutos por defecto): un `MATCH_END` publicado en las particiones 1 y 2 a las 21:14:48 recién se procesó a las 21:17:57. No se pierde nada (el consumer usa `auto-offset-reset: earliest`), pero en una demo parece que la alerta no llega. Probablemente explica parte de la demora de R-11, y contradice `explicacion_mensajeria_kafka.md`, que da por hecho que el tópico siempre lo crea el `NewTopic`. Opción más simple: declarar el mismo `NewTopic` también en `alert-dispatcher-service`, así el tópico existe con 3 particiones antes de que arranque el listener, levante primero el servicio que levante. **Para el coloquio, hasta resolverlo:** después de un `up` con Kafka recreado (ver R-15), esperar ~5 minutos o mandar un evento de prueba antes de la demo. Se resuelve en T-74.
- [ ] **R-15 · El volumen `kafka-data` no guarda nada: Kafka pierde tópicos, mensajes y offsets en cada `docker compose down`.** Detectado el 2026-10-01: el compose monta `kafka-data` en `/var/lib/kafka/data`, pero el broker escribe en `/tmp/kafka-logs` (el default de Kafka, porque el compose no setea `KAFKA_LOG_DIRS`). Verificado con `ls` dentro del contenedor: `/var/lib/kafka/data` vacío y `/tmp/kafka-logs` con `match-events-*` y `__consumer_offsets-*`. `restart` y `stop` no lo muestran porque el filesystem del contenedor sobrevive, pero `down` lo borra. Contradice la tabla de volúmenes de `explicacion_docker_compose.md` ("los mensajes del sistema de mensajería Kafka") y hace que R-14 se repita en cada `up` posterior a un `down`, no solo la primera vez. Opción más simple: `KAFKA_LOG_DIRS: /var/lib/kafka/data` en el servicio `kafka` del compose. Se resuelve en T-74.

---

## 4. Organización de la documentación

Estructura de `/docs`:

```
docs/
├── enunciado.md                      # enunciado original (no se toca)
├── resolucion_propuesta.md           # informe entregado por el grupo (no se toca)
├── planificacion_global.md           # este archivo: plan + seguimiento
├── plan_XX_<slug>.md                 # plan de implementación de una tarea (se crea al tomarla)
├── explicacion_<tema>.md             # material para entender el proyecto
└── assets/                           # imágenes y mockups
```

**Convenciones**
- `plan_*`: el número coincide con el ID de la tarea (`plan_11_auth_jwt.md` ↔ T-11). Se crean **de a uno, al tomar la tarea**, no todos por adelantado.
- `explicacion_*`: se escriben *después* de implementar algo no obvio, no antes.
- Cada tarea de esta planificación enlaza a su `plan_*` una vez creado.

**README vs CLAUDE.md:** el `README.md` es la fuente sobre el dominio, escrita para una persona que quiera entender o ejecutar el proyecto. `CLAUDE.md` es la guía de trabajo para la IA (metodología, criterios de código, decisiones vigentes, gotchas) y no repite el dominio: lo enlaza.

---

## 5. Carriles de trabajo

Con 3 integrantes, después de la Fase 0 se puede trabajar en paralelo:

| Carril | Responsable | Alcance |
|---|---|---|
| **A — Identidad** | _(asignar)_ | `fan-identity-service`: auth, JWT, suscripciones, historial |
| **B — Dominio deportivo y alertas** | _(asignar)_ | `match-ingestion-service` + `alert-dispatcher-service` + Kafka |
| **C — Frontend y observabilidad** | _(asignar)_ | `frontend-app` + Prometheus/Grafana/Loki |

La Fase 0 y la Fase 2 se hacen **juntos**: definen contratos y tocan puntos de integración.

---

## 6. Tareas

### Fase 0 — Cimientos (bloqueante, los tres juntos)

- [x] **T-00 · Reorganizar la documentación** — `docs/`, `README.md`, `CLAUDE.md` · depende de: — · todos
  - **Qué:** aplicar la estructura de la sección 4: separar responsabilidades de README y CLAUDE, eliminar la duplicación, crear `docs/assets/`, y corregir los nombres heredados de la exportación del informe.
  - **Por qué:** los tres integrantes van a trabajar con IA sobre el mismo repo; si CLAUDE.md no fija la metodología y las convenciones, cada IA va a generar código con criterios distintos.
  - **Afecta:** `README.md`, `CLAUDE.md`, `docs/`.
  - **Requisitos:**
    - [x] `README.md` conserva dominio, arquitectura, tecnologías, cómo levantar y puertos; agrega un índice de `/docs`.
    - [x] `CLAUDE.md` deja de repetir el dominio y suma metodología de trabajo + criterios de código + prohibiciones.
    - [x] Ambos dejan de mencionar `frontend-app/` como existente (se marca como pendiente) y usan el nombre real de la carpeta raíz.
    - [x] Mockups movidos a `docs/assets/` con nombres descriptivos y enlaces de `resolucion_propuesta.md` actualizados.
  - **Terminada cuando:** un integrante que no escribió los docs puede leer solo el README y levantar el proyecto, y CLAUDE.md no repite nada que ya esté en el README.

- [x] **T-01 · Congelar el contrato de APIs y el esquema del evento Kafka** — transversal · depende de: — (D-1 a D-6 ya resueltas) · todos · [plan](plan_01_contratos_api.md) · [contrato](explicacion_contratos_api.md)
  - **Qué:** escribir `docs/explicacion_contratos_api.md` con **todos** los endpoints (método, path, request, response, códigos de error, si es público/autenticado/interno) y el JSON del evento que viaja por Kafka, incluyendo nombre del tópico y clave de partición.
  - **Por qué:** es lo único que permite que los tres carriles avancen en paralelo sin pisarse. Sin esto, el frontend no sabe qué consumir y alert-dispatcher no sabe qué pedirle a fan-identity (hoy el Feign client apunta a un path inventado, R-5).
  - **Afecta:** ningún código todavía; condiciona todas las tareas de Fase 1 y 4.
  - **Requisitos:**
    - [x] Endpoints públicos de auth (`/api/fans/auth/**`).
    - [x] Endpoints del fan autenticado: suscripciones y notificaciones.
    - [x] Endpoint de suscriptores por equipo que consume alert-dispatcher (reemplaza el path provisorio).
    - [x] Endpoint interno de persistencia de notificación (callback de D-3), bajo un prefijo **fuera** de `/api/**` para que el gateway no lo exponga.
    - [x] Catálogo de ligas y equipos en `fan-identity-service`, bajo `/api/catalog/**` (por D-1).
    - [x] Consulta de partidos y eventos en `match-ingestion-service`.
    - [x] Endpoint de ingesta de eventos (el que usa el admin para simular).
    - [x] Esquema del evento Kafka: nombre del tópico, campos, tipos, ejemplo completo.
    - [x] Formato único de error para todos los servicios.
  - **Terminada cuando:** cada carril puede implementar su parte sin preguntarle nada a los otros dos.

- [x] **T-02 · Configuración base ejecutable y prueba de humo del stack** — los 5 servicios Spring · depende de: — · todos
  - **Qué:** completar los `application.yaml` de los 3 microservicios (puerto, cliente Eureka, datasource/Mongo/Kafka con valores por defecto para correr local), y verificar de punta a punta que `docker compose up --build` levanta todo y los 5 servicios aparecen registrados en Eureka.
  - **Por qué:** R-1 y R-3. Hoy la configuración vive solo en variables de entorno de Docker, así que nadie puede correr un microservicio suelto para desarrollar; y el stack (Boot 4.1 / Java 25 / Spring Cloud 2025.1.2) nunca se ejecutó. Si algo de esto no compila, bloquea el proyecto entero y hay que saberlo ahora, no en la semana de entrega.
  - **Afecta:** `*/src/main/resources/application.yaml`, eventualmente `pom.xml` y `docker-compose.yml`.
  - **Requisitos:**
    - [x] Cada microservicio define `server.port` y `eureka.client.service-url.defaultZone` con default local, sobreescribible por variable de entorno (las de `docker-compose.yml` siguen funcionando).
    - [ ] `mvn spring-boot:run` levanta cada microservicio contra la infra local sin editar archivos. *(Sin verificar: esta máquina no tiene Maven ni JDK 25 instalados, solo Docker. Los defaults locales ya están en los `application.yaml`, pero falta que alguien con Maven+JDK25 lo confirme.)*
    - [x] `docker compose up --build` completa sin errores y todos los contenedores quedan `healthy`.
    - [x] El dashboard de Eureka en `:8761` muestra los 4 clientes registrados.
    - [x] Se confirma que el namespace `spring.cloud.gateway.server.webmvc.routes` es el correcto para esta versión (ver `CLAUDE.md`).
    - [x] Cualquier problema de versiones se documenta en `CLAUDE.md`.
  - **Terminada cuando:** cualquier integrante clona el repo, corre `docker compose up --build` y ve los 4 servicios en Eureka. ✅ Verificado 2026-09-17: los 9 contenedores quedan `healthy` y los 4 clientes (FAN-IDENTITY-SERVICE, MATCH-INGESTION-SERVICE, ALERT-DISPATCHER-SERVICE, CLOUD-GATEWAY) aparecen `UP` en Eureka. Hizo falta agregar `@EnableEurekaServer` a `EurekaServerApplication` (ver gotcha en `CLAUDE.md`).

### Fase 1 — Núcleo de dominio (paralelizable en 3 carriles)

#### Carril A — `fan-identity-service`

- [x] **T-10 · Modelo de datos, catálogo y persistencia** — `fan-identity-service` · depende de: T-01, T-02 · carril A
  - **Qué:** entidades JPA `Fan`, `Subscription`, `League` y `Team` (+ rol, según D-5), repositorios y datos semilla (ligas y equipos reales, algunos fans y un admin).
  - **Por qué:** es la base de todo el carril A. Por D-1 el catálogo deportivo vive acá, así que la suscripción puede tener FK real al equipo: es la justificación de haber elegido PostgreSQL.
  - **Afecta:** `.../fan_identity_service/` (paquetes de dominio y repositorio), `application.yaml`.
  - **Requisitos:**
    - [x] `Fan` con email único, contraseña hasheada (BCrypt) y rol.
    - [x] `League` y `Team`, con `Team` perteneciendo a una `League`.
    - [x] `Subscription` con FK a `Fan` y a `Team`, y restricción de unicidad para no duplicar suscripciones.
    - [x] El identificador de `Team` es el mismo `teamId` que viaja en los eventos de Kafka y que guarda `match-ingestion-service`.
    - [x] Datos semilla cargados al arrancar, idempotentes (no duplican al reiniciar).
    - [x] Las tablas se crean solas contra el Postgres del compose.
  - **Terminada cuando:** el servicio arranca contra Postgres, crea el esquema y los datos semilla (ligas, equipos, fans, admin) se ven consultando la base. ✅ Verificado en ejecución en Docker (2026-09-17).

- [x] **T-11 · Registro, login y emisión de JWT** — `fan-identity-service` · depende de: T-10 · carril A
  - **Qué:** endpoints públicos de registro y login, hash de contraseñas, emisión de JWT firmado, y la `SecurityFilterChain` del servicio.
  - **Por qué:** cubre el requisito obligatorio de seguridad y el punto 3.1 de la resolución. Además desactiva el basic auth por defecto que hoy rompería todo (R-4).
  - **Afecta:** `.../fan_identity_service/` (auth, security, config).
  - **Requisitos:**
    - [x] Registro valida email único y devuelve error claro si ya existe.
    - [x] Login devuelve JWT con el id del fan y su rol en los claims.
    - [x] Contraseñas hasheadas con BCrypt, nunca en texto plano ni en logs.
    - [x] Rutas de auth públicas, el resto autenticado.
    - [x] La clave de firma se lee de configuración (variable de entorno), con default solo para desarrollo local.
  - **Terminada cuando:** se puede registrar un fan, loguearlo, obtener un JWT y usarlo para acceder a un endpoint protegido del servicio. ✅ Verificado con endpoints en ejecución a través del gateway (2026-09-17).

- [x] **T-12 · Suscripciones del fan** — `fan-identity-service` · depende de: T-11 · carril A · [plan](plan_12_suscripciones_fan.md)
  - **Qué:** seguir un equipo, dejar de seguirlo y listar las suscripciones del fan autenticado.
  - **Por qué:** es la funcionalidad central del rol Fan (punto 3.2) y lo que alimenta el fan-out de alertas.
  - **Afecta:** `.../fan_identity_service/` (subscription).
  - **Requisitos:**
    - [x] Los tres endpoints operan **siempre** sobre el fan del JWT, nunca sobre un id que venga en el body.
    - [x] Seguir dos veces el mismo equipo no duplica ni falla con error 500.
    - [x] Seguir un equipo inexistente se rechaza con un error claro (la FK a `Team` lo garantiza).
    - [x] Dejar de seguir algo no suscripto devuelve un código coherente, no una excepción.
    - [x] El listado devuelve el nombre del equipo y su liga, resueltos por join (sin desnormalizar).
  - **Terminada cuando:** con un JWT válido se puede seguir, listar y dejar de seguir equipos, y los cambios persisten tras reiniciar el servicio. ✅ Verificado en ejecución en Docker (2026-09-17).

- [x] **T-13 · Endpoint de suscriptores por equipo** — `fan-identity-service` · depende de: T-12 · carril A · [plan](plan_13_endpoint_suscriptores.md)
  - **Qué:** el endpoint que consume `alert-dispatcher-service` para saber a quién notificar, según el contrato de T-01.
  - **Por qué:** es la pieza que cierra el fan-out y la llamada que protege el Circuit Breaker.
  - **Afecta:** `.../fan_identity_service/` (controlador interno), `FanIdentityClient` en `alert-dispatcher-service`.
  - **Requisitos:**
    - [x] Devuelve la lista de fans suscriptos a un equipo, con los campos exactos del contrato.
    - [x] Equipo sin suscriptos devuelve lista vacía, no 404.
    - [x] Queda fuera del prefijo que el gateway expone (no alcanzable desde el host).
  - **Terminada cuando:** `alert-dispatcher-service` obtiene la lista real vía Feign dentro de la red Docker y el contrato coincide con `FanSubscriberDTO`. ✅ Verificado 2026-09-17: `GET /internal/fans/subscribers/team-colon` (con un fan suscripto) devuelve `[{"fanId":"2","email":"fan1@quienjuega.com"}]`; `team-union` (sin suscriptores) devuelve `[]`; `GET http://localhost:8080/internal/fans/subscribers/team-colon` desde el host da `404` (el gateway no rutea `/internal/**`). De paso se corrigió un problema real de infra: el healthcheck de `kafka` tenía `timeout: 5s` pero el comando tarda ~7s en responder, dejándolo `unhealthy` siempre y bloqueando a `alert-dispatcher-service`/`match-ingestion-service` (ver gotcha en `CLAUDE.md`, ahora `timeout: 12s`).

- [x] **T-14 · Historial de notificaciones** — `fan-identity-service` · depende de: T-12 · carril A · [plan](plan_14_historial_notificaciones.md)
  - **Qué:** entidad `Notification`, endpoint **interno** de alta (el callback de D-3) y endpoint del fan para listar su historial.
  - **Por qué:** el Fan tiene que poder ver su historial de alertas y alert-dispatcher no tiene base propia.
  - **Afecta:** `.../fan_identity_service/` (notification).
  - **Requisitos:**
    - [x] `Notification` guarda a qué fan pertenece, el texto, el evento que la originó y la fecha.
    - [x] Alta interna, fuera del prefijo público del gateway.
    - [x] El listado devuelve solo las notificaciones del fan del JWT, ordenadas de más nueva a más vieja.
  - **Terminada cuando:** insertando una notificación por el endpoint interno, el fan la ve en su historial. ✅ Verificado en ejecución en Docker (2026-09-17): `POST /internal/fans/notifications` (dentro de la red, sin curl en los contenedores de la app, se usó un contenedor `curlimages/curl` efímero en `tp-deportes-nube_tp-nube-network`) devuelve `201` con `{"id":...}`; `GET /api/fans/me/notifications` vía gateway con el JWT de ese fan muestra ambas notificaciones ordenadas de más nueva a más vieja; un `fanId` inexistente devuelve `404 FAN_NOT_FOUND`; y `POST http://localhost:8080/internal/fans/notifications` desde el host da `404` propio del gateway (ruta no declarada), igual que en T-13.

- [x] **T-15 · Endpoints de catálogo de ligas y equipos** — `fan-identity-service`, `cloud-gateway` · depende de: T-10, T-11 · carril A · [plan](plan_15_endpoints_catalogo.md)
  - **Qué:** endpoints de lectura para listar ligas y buscar equipos por nombre, más la ruta `/api/catalog/**` en el gateway.
  - **Por qué:** el Fan "busca equipos o ligas en el catálogo" antes de suscribirse, y por D-1 el catálogo vive en este servicio. Va bajo su propio prefijo porque servirlo desde `/api/fans/**` sería confuso.
  - **Afecta:** `.../fan_identity_service/` (controlador de catálogo), `cloud-gateway/application.yaml`.
  - **Requisitos:**
    - [x] Listado de ligas y listado de equipos, con filtro por nombre.
    - [x] Los equipos devuelven el `teamId` que acepta T-12 para suscribirse.
    - [x] La ruta `/api/catalog/**` del gateway enruta a `fan-identity-service` vía Eureka.
  - **Terminada cuando:** vía gateway se puede buscar un equipo por nombre y usar el `teamId` devuelto para suscribirse. ✅ Verificado en ejecución en Docker (2026-09-17): `GET /api/catalog/leagues` y `GET /api/catalog/teams` (sin filtro, por `name`, por `leagueId` y combinando ambos con AND real) devuelven lo esperado vía gateway con JWT; sin token da `401`; se tomó el `id` de `Unión` del catálogo y se usó en `POST /api/fans/subscriptions` → `201`. De paso se corrigió un bug real: `TeamRepository.search` fallaba con `500` (`function lower(bytea) does not exist`) cuando `name` venía `null`, porque Postgres no puede inferir el tipo del parámetro dentro de `LOWER(CONCAT(...))`; se resolvió con un `CAST(:name AS string)` explícito en el JPQL.

#### Carril B — `match-ingestion-service` y `alert-dispatcher-service`

> **Estado 2026-09-26:** las seis tareas (T-20 a T-31) se verificaron ejecutando el stack completo en Docker (ver detalle en cada tarea). Nota para la próxima vez: el primer intento de verificación dio falso negativo porque `docker compose up` (sin `--build`) reusó imágenes de días antes que no tenían este código — ver **R-9**. Con `--build` todo funcionó.

- [x] **T-20 · Modelo de documentos de partidos y eventos** — `match-ingestion-service` · depende de: T-01, T-02 · carril B · [plan](plan_20_modelo_partidos_eventos.md)
  - **Qué:** documentos Mongo para partido y evento deportivo, con repositorios y datos semilla (un par de partidos).
  - **Por qué:** es la justificación de haber elegido MongoDB: los eventos son heterogéneos y un esquema de documentos flexible encaja mejor que uno relacional. Por D-1 el catálogo de ligas/equipos **no** vive acá.
  - **Afecta:** `.../match_ingestion_service/` (dominio y repositorios).
  - **Requisitos:**
    - [x] El documento de evento soporta tipos distintos (gol, tarjeta, fin de partido) sin campos obligatorios que solo apliquen a uno.
    - [x] Partido y evento referencian `teamId` como string: el mismo identificador que usa el catálogo de T-10, sin FK entre servicios.
    - [x] Semilla idempotente.
  - **Terminada cuando:** el servicio arranca contra Mongo y los partidos semilla se pueden consultar desde la base. ✅ Verificado en Docker (2026-09-26): `GET /api/matches` vía gateway devuelve los 3 partidos semilla (`match-001` a `match-003`) con sus `homeTeamId`/`awayTeamId`.
  - **Documentado en:** [explicacion_modelo_mongo.md](explicacion_modelo_mongo.md).

- [x] **T-21 · Endpoint de ingesta de eventos** — `match-ingestion-service` · depende de: T-20 · carril B · [plan](plan_21_ingesta_eventos.md)
  - **Qué:** el endpoint que recibe un evento deportivo de un proveedor externo, lo valida y lo persiste. Es también el que usa el admin para simular.
  - **Por qué:** es la puerta de entrada de datos del sistema y el disparador de todo el flujo de punta a punta.
  - **Afecta:** `.../match_ingestion_service/` (controlador y servicio de ingesta).
  - **Requisitos:**
    - [x] Valida el formato y rechaza eventos incompletos con un error claro, sin persistirlos.
    - [x] Rechaza eventos cuyo partido no exista en su propia base. **No** valida el `teamId` contra el catálogo: por D-1 vive en otro servicio y no vale la pena otra llamada entre servicios.
    - [x] Persiste el evento y devuelve su identificador.
  - **Terminada cuando:** un POST con un evento válido lo deja guardado en Mongo, y uno inválido devuelve error sin ensuciar la base. ✅ Verificado en Docker (2026-09-26): un `GOAL` sin `minute` da `400 INVALID_EVENT`; contra un `matchId` inexistente da `404 MATCH_NOT_FOUND`; un evento válido da `201` con `eventId` y actualiza el marcador del partido.

- [x] **T-22 · Consulta de partidos y eventos** — `match-ingestion-service` · depende de: T-20 · carril B · [plan](plan_22_consulta_partidos.md)
  - **Qué:** endpoints de lectura para que el frontend liste los partidos y el historial de eventos de un partido. El catálogo de ligas y equipos **no** está acá: es T-15.
  - **Por qué:** alimenta el muro de eventos en vivo del panel principal (ver el mockup en `docs/assets/mockup_panel_principal.png`).
  - **Afecta:** `.../match_ingestion_service/` (controlador de consulta).
  - **Requisitos:**
    - [x] Listado de partidos, con filtro por equipo.
    - [x] Eventos de un partido ordenados cronológicamente.
    - [x] Las respuestas incluyen el `teamId` de cada equipo participante para que el frontend pueda cruzarlo con el catálogo.
  - **Terminada cuando:** vía gateway se pueden listar partidos y ver los eventos de uno en orden cronológico. ✅ Verificado en Docker (2026-09-26): `GET /api/matches?teamId=team-colon` filtra correctamente; `GET /api/matches/{matchId}/events` devuelve los eventos ingestados; `GET /api/matches/match-inexistente/events` da `404 MATCH_NOT_FOUND`.

- [x] **T-23 · Publicación del evento en Kafka** — `match-ingestion-service` · depende de: T-21 · carril B · [plan](plan_23_publicacion_kafka.md)
  - **Qué:** tras persistir un evento, publicarlo en el tópico definido en T-01, con el esquema acordado.
  - **Por qué:** es el requisito obligatorio de mensajería asincrónica y desacopla la ingesta del despacho de alertas.
  - **Afecta:** `.../match_ingestion_service/` (productor Kafka), `application.yaml`.
  - **Requisitos:**
    - [x] El tópico se declara explícitamente (bean `NewTopic`), sin depender de la auto-creación del broker.
    - [x] El mensaje respeta exactamente el esquema de T-01 y usa `teamId` como clave.
    - [x] Si Kafka está caído, el evento igual queda persistido y el fallo se loguea; no se pierde el POST.
  - **Terminada cuando:** al ingestar un evento, el mensaje se ve en Kafka UI (`:8090`) en el tópico correcto. ✅ Verificado (2026-09-26) leyendo el tópico `match-events` con `kafka-console-consumer.sh`: el mensaje publicado coincide campo a campo con el `MatchEventDTO` de la ingesta.
  - **Documentado en:** [explicacion_mensajeria_kafka.md](explicacion_mensajeria_kafka.md) (junto con T-30).

- [x] **T-30 · Consumer Kafka y orquestación del fan-out** — `alert-dispatcher-service` · depende de: T-23, T-13, T-14 · carril B · [plan](plan_30_consumer_fan_out.md)
  - **Qué:** consumir el tópico, resolver los suscriptos con `FanIdentityLookupService` y persistir una notificación por fan vía el callback a fan-identity.
  - **Por qué:** es el corazón del sistema: convierte un evento deportivo en alertas personalizadas.
  - **Afecta:** `.../alert_dispatcher_service/` (listener, servicio de despacho, cliente del callback).
  - **Requisitos:**
    - [x] El listener deserializa el evento según el esquema de T-01.
    - [x] Se genera una notificación por cada fan suscripto, con un texto legible construido a partir del evento.
    - [x] Un evento sin suscriptos se procesa sin error y se loguea.
    - [x] Un fallo al notificar a un fan no impide notificar a los demás.
  - **Terminada cuando:** al ingestar un evento de un equipo que un fan sigue, ese fan ve la notificación en su historial. ✅ Verificado en Docker (2026-09-26): un `GOAL` de `team-colon` generó la notificación `"¡Gol! Jugador Test (10') — marcador 1-0"` visible en `GET /api/fans/me/notifications` de `fan1` (suscripto), con el mismo `eventId` de la ingesta; un evento de `team-manchester-city` (sin suscriptos) se procesó con `subscriberCount: 0` y sin error. El requisito de "un fallo no bloquea a los demás" se confirmó por lectura de código (`AlertDispatchService.dispatch`, try/catch por fan dentro del loop) más el test `AlertDispatchServiceTest` con Mockito que ya trae el commit; no se simuló un fallo parcial real entre varios fans en Docker.

- [x] **T-31 · Cerrar el Circuit Breaker y exponer su estado** — `alert-dispatcher-service` · depende de: T-30 · carril B · [plan](plan_31_circuit_breaker_estado.md)
  - **Qué:** reemplazar el fallback vacío por uno real (el `TODO` de `FanIdentityLookupService`) y exponer el estado del despachador según D-6.
  - **Por qué:** Resilience4J es la característica extra elegida y hoy el fallback descarta eventos en silencio, que es justamente lo que no hay que mostrar en el coloquio.
  - **Afecta:** `.../alert_dispatcher_service/` (service, config, controlador de estado).
  - **Requisitos:**
    - [x] El fallback deja registro de los eventos no despachados en vez de descartarlos.
    - [x] Endpoint de estado con últimos eventos procesados y estado del circuito.
    - [x] Se documenta en `docs/explicacion_circuit_breaker.md` cómo demostrar la apertura del circuito (bajar fan-identity y mostrar el comportamiento).
  - **Terminada cuando:** apagando `fan-identity-service` e ingestando eventos, el circuito abre, alert-dispatcher sigue respondiendo y el comportamiento es reproducible con los pasos documentados. ✅ Verificado en Docker (2026-09-26) siguiendo al pie de la letra la sección 5 de `explicacion_circuit_breaker.md`: con `fan-identity-service` apagado, 6 eventos dieron `201` igual (match-ingestion no depende de fan-identity) y `GET /api/alerts/status` pasó a `circuitState: "OPEN"` con los 6 en `undispatchedEvents`; `alert-dispatcher-service` siguió respondiendo en todo momento. Al reiniciar `fan-identity-service` y reintentar, el circuito pasó por `HALF_OPEN` y volvió a `CLOSED`, y `fan1` vio las notificaciones nuevas en su historial. (Un primer intento de recuperación falló por la caché del load balancer de `alert-dispatcher-service`, no por el circuito en sí — se resolvió solo tras esperar un poco más a que refrescara.)

### Fase 2 — Integración y seguridad (los tres juntos)

- [x] **T-40 · Seguridad y CORS en el gateway** — `cloud-gateway` · depende de: T-11 · todos · [plan](plan_40_seguridad_cors_gateway.md)
  - **Qué:** filtro que valida el JWT en el gateway, lista de rutas públicas y configuración de CORS para el frontend.
  - **Por qué:** el gateway es el único punto de entrada; validar ahí evita repetir seguridad en cada microservicio. Sin CORS, el frontend no puede consumir nada.
  - **Afecta:** `cloud-gateway` (config y filtro), `pom.xml`.
  - **Requisitos:**
    - [x] Request sin token o con token inválido a una ruta protegida devuelve 401 sin llegar al microservicio.
    - [x] Rutas de auth accesibles sin token.
    - [x] El gateway propaga el token al microservicio de destino.
    - [x] CORS habilitado para el origen del frontend.
  - **Terminada cuando:** desde el navegador se puede loguear y consumir endpoints protegidos, y un token adulterado se rechaza en el gateway. ✅ Verificado en Docker (2026-09-29): request sin token y con token adulterado dan 401 directo en el gateway; login/registro son públicos; rol ADMIN verificado con 403 ante fans en ingesta.
  - **Documentado en:** [explicacion_seguridad.md](explicacion_seguridad.md) (junto con T-11 y T-41).

- [x] **T-41 · Blindar los endpoints internos** — `cloud-gateway`, `fan-identity-service` · depende de: T-40, T-13, T-14 · todos · [plan](plan_41_blindar_endpoints_internos.md)
  - **Qué:** verificar que los endpoints internos (suscriptores por equipo y callback de notificaciones) no sean alcanzables desde el host, y protegerlos entre servicios.
  - **Por qué:** el callback de notificaciones escribe en la base sin JWT de usuario; si queda expuesto, cualquiera puede inyectar notificaciones a cualquier fan.
  - **Afecta:** rutas del gateway, `SecurityFilterChain` de fan-identity, `docker-compose.yml`.
  - **Requisitos:**
    - [x] Ningún endpoint interno responde desde `localhost:8080`.
    - [x] La llamada interna entre servicios se autentica (secreto compartido por variable de entorno).
    - [x] El secreto no queda hardcodeado en el código.
  - **Terminada cuando:** los endpoints internos funcionan entre contenedores y devuelven 404/401 desde afuera. ✅ Verificado en Docker (2026-09-29): `/internal/**` devuelve 404 desde el host a través del gateway; entre servicios `alert-dispatcher-service` se autentica usando `INTERNAL_API_KEY` de `.env` vía `FanIdentityClient` y las alertas se persisten correctamente.
  - **Documentado en:** [explicacion_seguridad.md](explicacion_seguridad.md).

- [x] **T-42 · Verificación del flujo de punta a punta** — transversal · depende de: T-30, T-40, T-41 · todos · [plan](plan_42_flujo_punta_a_punta.md)
  - **Qué:** ejercitar el recorrido completo: registro → login → buscar equipo → suscribirse → ingestar evento → ver la notificación.
  - **Por qué:** es exactamente la demo del coloquio; hay que saber que funciona antes de construir el frontend encima.
  - **Afecta:** ningún código nuevo; puede destapar correcciones en tareas previas.
  - **Requisitos:**
    - [x] El recorrido completo funciona pasando siempre por el gateway.
    - [x] Quedan registrados los pasos exactos para reproducirlo.
    - [x] Los errores encontrados se corrigen o se anotan como tareas nuevas en este documento.
  - **Terminada cuando:** el flujo se puede reproducir desde cero con `docker compose down -v && docker compose up --build`. ✅ Verificado en Docker (2026-09-29): stack levantado desde cero con volúmenes limpios; se corrieron en secuencia los 16 pasos de `plan_42_flujo_punta_a_punta.md` sin fallas. Se confirmó el fan-out vía Kafka, el registro de alerta en Postgres, la consulta de alertas por el fan, el estado `CLOSED` de Resilience4J y la persistencia de suscripciones y alertas tras `docker compose restart`.

### Fase 3 — Requisitos transversales del enunciado

- [x] **T-50 · Telemetría de métricas de rendimiento** — los 5 servicios Spring + compose · depende de: T-02 · carril C
  - **Qué:** Actuator + Micrometer con registro Prometheus en los 5 servicios, Prometheus y Grafana en el compose, y un dashboard con las métricas clave.
  - **Por qué:** es uno de los requisitos obligatorios del enunciado. De paso permite migrar los healthchecks TCP a `/actuator/health` (R-6).
  - **Afecta:** los 5 `pom.xml` y `application.yaml`, `docker-compose.yml`, configuración de Prometheus y Grafana.
  - **Requisitos:**
    - [x] Los 5 servicios exponen `/actuator/prometheus`.
    - [x] Prometheus scrapea a los 5 y Grafana los grafica.
    - [x] El dashboard muestra al menos: latencia y volumen de requests HTTP, uso de memoria/JVM y estado del circuito de Resilience4J.
    - [x] Los healthchecks de `docker-compose.yml` pasan a `/actuator/health`.
    - [x] Los endpoints de Actuator no quedan expuestos públicamente por el gateway.
  - **Ojo (desde T-40):** el gateway pide JWT para todo lo que no sea `/api/fans/auth/**`, incluido su propio `/actuator/**`. Se resolvió moviendo Actuator a `management.server.port: 8084` interno y no publicándolo al host. Las peticiones a `localhost:8080/actuator/**` reciben 401.
  - **Terminada cuando:** generando tráfico con el flujo de T-42, las métricas se mueven en el dashboard de Grafana. ✅ Verificado en Docker (2026-09-29): los 11 contenedores arrancaron saludables con healthchecks HTTP reales vía `curl`; Prometheus scrapea a los 5 targets en estado `UP`; Grafana arranca en `http://localhost:3001` con datasource y dashboard aprovisionados automáticamente; se generó tráfico y se verificaron en Prometheus y Grafana las series de requests HTTP por servicio, memoria JVM Heap y estado `CLOSED` de Resilience4J; Actuator queda blindado de accesos externos (401 en puerto 8080).

- [x] **T-51 · Almacén centralizado de logs** — transversal · depende de: T-50 · carril C
  - **Qué:** Loki + Promtail en el compose, logs de los 5 servicios en formato consistente y un identificador de correlación que permita seguir un evento a través de los servicios.
  - **Por qué:** es el otro requisito obligatorio del enunciado que falta. Reutiliza el Grafana de T-50.
  - **Afecta:** `docker-compose.yml`, configuración de logging de los servicios.
  - **Requisitos:**
    - [x] Promtail recolecta los logs de todos los contenedores y los envía a Loki.
    - [x] Desde Grafana se pueden filtrar logs por servicio y por nivel.
    - [x] Un identificador de correlación viaja desde la ingesta hasta la notificación y aparece en los logs de los tres microservicios.
    - [x] Ninguna contraseña, token ni dato sensible aparece en los logs.
  - **Terminada cuando:** disparando un evento se puede seguir su recorrido completo en Grafana filtrando por el identificador de correlación. ✅ Verificado en Docker (2026-09-29): Loki y Promtail recolectan logs de los 13 contenedores a través del socket de Docker; etiquetas `service` y `level` indexadas y filtrables; Grafana configurado con datasource Loki; se ingestó un evento deportivo y se verificó que el identificador de correlación (`eventId`) apareció en secuencia cronológica en `match-ingestion-service`, `alert-dispatcher-service` y `fan-identity-service`; 0 credenciales ni datos sensibles en logs.

### Fase 4 — Frontend

- [x] **T-60 · Scaffolding de `frontend-app`** — `frontend-app/` · depende de: T-02 · carril C · [plan](plan_60_scaffolding_frontend.md)
  - **Qué:** crear el proyecto Next.js (Pages Router), su Dockerfile y su servicio en `docker-compose.yml`, apuntando al gateway.
  - **Por qué:** es el cuarto componente que exige el enunciado y hoy la carpeta no existe (R-2). Puede arrancar en paralelo con la Fase 1.
  - **Afecta:** `frontend-app/`, `docker-compose.yml`, `.gitignore`.
  - **Requisitos:**
    - [x] Proyecto Next.js con Pages Router que levanta en desarrollo.
    - [x] La URL del gateway se lee de variable de entorno, nunca hardcodeada.
    - [x] Dockerfile funcional y servicio agregado al compose.
    - [x] Una página inicial que confirme que el contenedor responde.
  - **Terminada cuando:** `docker compose up --build` levanta también el frontend y se accede desde el navegador. ✅ Verificado el 2026-09-30: `docker compose up -d --build frontend-app` deja el contenedor `healthy` y `http://localhost:3000` responde `200`. Encontrado y corregido un bug real en el camino: el server standalone de Next.js escuchaba solo en la IP interna del contenedor (no en `0.0.0.0`) porque heredaba el `HOSTNAME` que Docker setea automáticamente — ver gotcha en `CLAUDE.md`.

- [x] **T-61 · Capa de acceso a la API y sesión** — `frontend-app/` · depende de: T-60, T-01 · carril C · [plan](plan_61_api_sesion.md)
  - **Qué:** un único módulo cliente que hable con el gateway, guarde el JWT, lo adjunte a cada request y maneje el 401.
  - **Por qué:** centralizar esto evita repetir `fetch` y manejo de token en cada pantalla.
  - **Afecta:** `frontend-app/` (servicios y contexto de sesión).
  - **Requisitos:**
    - [x] Todas las llamadas pasan por este módulo y por el gateway, nunca directo a un microservicio.
    - [x] El token se adjunta automáticamente y el 401 redirige al login.
    - [x] Rutas privadas inaccesibles sin sesión.
  - **Terminada cuando:** ninguna pantalla hace `fetch` por su cuenta ni conoce puertos de microservicios. ✅ `lib/api.js` es el único lugar que llama `fetch` en todo `frontend-app` (grep confirma que ninguna página lo hace directo); `RequireAuth` (usado por T-63/T-64/T-65) redirige a `/login` sin sesión. Sin browser disponible en este entorno para grabar la interacción real: verificado simulando con `curl` exactamente los mismos requests que hace cada función de `lib/api.js` contra el gateway real, todos con el resultado esperado.
  - **Bug real encontrado por el usuario probando en el browser (2026-09-30):** `POST /api/fans/auth/login` no devuelve `email` en el contrato (solo `token`/`fanId`/`role`), pero la sesión guardada en `localStorage` lo necesitaba para mostrarlo en la UI (T-63/T-64/T-65). `curl` no lo detectó porque nunca ejercita `localStorage` ni el estado de React. Corregido en `SessionContext.login`: se guarda el `email` que el usuario ya tipeó junto con la respuesta del backend.

- [x] **T-62 · Pantallas de registro y login** — `frontend-app/` · depende de: T-61, T-11 · carril C · [plan](plan_62_pantallas_auth.md)
  - **Qué:** las pantallas de acceso del fan, siguiendo el mockup de la resolución.
  - **Por qué:** es la puerta de entrada de la aplicación y de la demo.
  - **Afecta:** `frontend-app/` (páginas de auth).
  - **Requisitos:**
    - [x] Registro y login funcionales contra el gateway.
    - [x] Errores del backend (email duplicado, credenciales inválidas) mostrados de forma legible.
    - [x] Tras loguear, redirige al panel principal.
  - **Terminada cuando:** un usuario nuevo se registra, inicia sesión y entra al panel sin tocar la consola. ✅ Verificado el 2026-09-30 con el stack completo en Docker: `/login` y `/register` sirven `200` y compilan sin warnings de ESLint; el flujo de registro→login se probó reproduciendo exactamente las llamadas que hace `lib/api.js` (nuevo fan `fan.frontend@quienjuega.com`, `201` en registro, JWT válido en login). Sin browser disponible en este entorno: no se grabó el click-through real del formulario, solo la lógica que dispara.

- [x] **T-63 · Catálogo y mis suscripciones** — `frontend-app/` · depende de: T-62, T-12, T-15 · carril C · [plan](plan_63_catalogo_suscripciones.md)
  - **Qué:** pantalla de búsqueda de equipos/ligas con seguir/dejar de seguir, y pantalla de suscripciones, según los mockups.
  - **Por qué:** es la funcionalidad principal del rol Fan.
  - **Afecta:** `frontend-app/` (páginas de catálogo y suscripciones).
  - **Requisitos:**
    - [x] Búsqueda de equipos por nombre.
    - [x] Seguir/dejar de seguir refleja el cambio sin recargar la página (estado de React, sin `router.reload()`).
    - [x] El listado de suscripciones muestra nombres de equipo, no identificadores.
  - **Terminada cuando:** un fan busca un equipo, lo sigue y lo ve aparecer en sus suscripciones. ✅ Verificado el 2026-09-30: reproduciendo las llamadas de `/subscriptions` contra el stack real — búsqueda de "river" en el catálogo, `POST /api/fans/subscriptions`, y el equipo aparece en `GET /api/fans/subscriptions` con nombre y liga resueltos. **Nota de alcance:** `/` (home autenticada) quedó como un resumen simple, no como el muro de partidos en vivo de `mockup_panel_principal.png` — esa pantalla no está cubierta por ninguna tarea de este documento (ver T-66 propuesta más abajo).

- [x] **T-64 · Centro de alertas** — `frontend-app/` · depende de: T-62, T-14 · carril C · [plan](plan_64_centro_alertas.md)
  - **Qué:** pantalla del historial de notificaciones del fan, según el mockup.
  - **Por qué:** cierra el recorrido del Fan y es donde se ve el resultado del flujo asincrónico.
  - **Afecta:** `frontend-app/` (página de alertas).
  - **Requisitos:**
    - [x] Notificaciones ordenadas de más nueva a más vieja con su fecha (orden ya garantizado por el backend, T-14; acá se formatea `createdAt`).
    - [x] Estado vacío claro cuando el fan todavía no tiene alertas.
    - [x] Se puede refrescar para ver alertas nuevas (botón, sin recargar la página).
  - **Terminada cuando:** con el fan logueado y un evento ingestado, la alerta aparece en la pantalla. ✅ Verificado el 2026-09-30 de punta a punta: se registró un fan nuevo, se suscribió a River Plate, el admin ingestó un gol de River, y `GET /api/fans/me/notifications` (la misma llamada que hace `/alerts`) devolvió la alerta con el mensaje legible generado por `alert-dispatcher-service`.

- [x] **T-65 · Panel de administrador** — `frontend-app/` · depende de: T-63, T-21, T-31 · carril C · [plan](plan_65_panel_admin.md)
  - **Qué:** pantalla de admin con el formulario para simular la carga de un evento deportivo y enlaces/estado de monitoreo.
  - **Por qué:** es el rol Administrador de la resolución y la forma de disparar la demo en vivo durante el coloquio.
  - **Afecta:** `frontend-app/` (páginas de admin).
  - **Requisitos:**
    - [x] Solo accesible con rol admin (`RequireAuth adminOnly`, reusa T-61/T-63).
    - [x] Formulario de simulación de evento que llama al endpoint de ingesta.
    - [x] Estado del despachador y enlaces a Eureka, Kafka UI y Grafana.
  - **Terminada cuando:** el admin dispara un evento desde la UI y el fan suscripto ve la alerta en su centro de alertas. ✅ Verificado el 2026-09-30, mismo flujo que T-64: se reprodujeron las llamadas exactas de `/admin` (`GET /api/matches` para poblar el selector, `POST /api/matches/{id}/events` para ingestar, `GET /api/alerts/status` para el estado) y el evento llegó como alerta real al fan suscripto.

- [ ] **T-66 · Panel Principal (muro de partidos en vivo)** — `frontend-app/` · depende de: T-63, T-22 · carril C · **propuesta, no asignada**
  - **Qué:** la pantalla de `docs/assets/mockup_panel_principal.png` — un muro con los partidos (`GET /api/matches`) y sus eventos recientes (`GET /api/matches/{id}/events`), cruzando `homeTeamId`/`awayTeamId` contra el catálogo para mostrar nombres.
  - **Por qué:** hay un mockup para esta pantalla y el backend que la alimentaría (T-22) ya existe, pero **ninguna tarea del plan original la cubre**. Se detectó al implementar T-63 (2026-09-30): el home autenticado (`/`) hoy es un resumen simple (cantidad de suscripciones/alertas + accesos directos), no el muro de partidos del mockup, siguiendo la regla de `CLAUDE.md` de no construir algo que no esté en una tarea.
  - **Afecta:** `frontend-app/` (`pages/index.js`).
  - **Requisitos:** (a definir si se toma la tarea)
    - [ ] Listado de partidos con estado (`SCHEDULED`/`LIVE`/`FINISHED`) y marcador, nombres de equipo resueltos contra el catálogo.
    - [ ] Eventos recientes por partido (opcional, según cuánto se quiera parecer al mockup).
  - **Terminada cuando:** — (sin definir hasta que se decida tomarla).

### Fase 5 — Cierre y entrega

- [x] **T-70 · Pruebas automatizadas mínimas** — transversal · depende de: T-42 · todos · [plan](plan_70_tests_automatizados.md)
  - **Qué:** tests de las reglas de negocio que más duelen si se rompen, más un test de contexto por servicio.
  - **Por qué:** el enunciado no exige tests, así que el objetivo es cubrir lo crítico sin invertir tiempo desproporcionado.
  - **Afecta:** `src/test/java` de los microservicios.
  - **Requisitos:**
    - [x] Cada servicio levanta su contexto en un test (detecta configuración rota).
    - [x] fan-identity: tests de registro/login y de no duplicar suscripciones.
    - [x] match-ingestion: test de rechazo de evento inválido.
    - [x] alert-dispatcher: test del fallback del Circuit Breaker (ya existía desde T-31: `AlertDispatchServiceTest`).
    - [x] `mvn test` pasa en los 5 servicios.
  - **Terminada cuando:** `mvn test` está en verde en todo el backend. ✅ Verificado el 2026-09-30: sin Maven/JDK local (ver gotcha de `CLAUDE.md`), se corrió `mvn test` para cada servicio vía `docker run` con el contenedor `maven:3.9-eclipse-temurin-25`, conectado a la infraestructura real ya levantada (`postgres`/`mongo`/`kafka`/`eureka-server`). `BUILD SUCCESS` en los 5: fan-identity-service (9 tests), match-ingestion-service (6), alert-dispatcher-service (8), eureka-server (1), cloud-gateway (1) — 25 en total, 0 fallos.

- [x] **T-71 · Colección de requests reproducible** — `docs/` · depende de: T-42 · todos
  - **Qué:** archivo `.http` (o colección equivalente) con todo el flujo en orden, ejecutable desde el editor.
  - **Por qué:** sirve para probar sin frontend y es el plan B si algo falla en la demo del coloquio.
  - **Afecta:** `docs/`.
  - **Requisitos:**
    - [x] Cubre el recorrido completo de T-42 en orden.
    - [x] El token se reutiliza entre requests sin copiar y pegar a mano (sintaxis de la extensión **REST Client** de VS Code: `# @name` + `{{nombre.response.body.$.campo}}`).
  - **Terminada cuando:** ejecutando la colección de arriba a abajo sobre un stack recién levantado, todas las respuestas son las esperadas. ✅ `docs/quien-juega.http`, verificado el 2026-09-30 reproduciendo los 16 pasos con `curl` (no hay extensión REST Client en este entorno para correr el `.http` directamente, así que se ejercitaron los mismos requests uno por uno, en el mismo orden). Los 16 dieron el resultado esperado. De paso salió **R-12** (búsqueda del catálogo sin tildes) y se confirmó que el manejo de UTF-8/tildes del backend es correcto — los `400`/`INVALID_EVENT` que aparecieron al principio eran de mi propio `curl` tipeado en Git Bash en Windows (codifica tildes como Latin-1, no UTF-8), no del código: con el mismo payload mandado desde un archivo UTF-8 real, igual que hace el `.http`, funciona perfecto.

- [x] **T-72 · Documentación final** — `docs/`, `README.md` · depende de: T-50, T-51, T-65 · todos
  - **Qué:** actualizar README y escribir las `explicacion_*` que falten (arquitectura, seguridad, mensajería, observabilidad).
  - **Por qué:** la entrega incluye el repositorio, y las explicaciones son el material de estudio del grupo para el coloquio.
  - **Afecta:** `README.md`, `docs/`.
  - **Requisitos:**
    - [x] README refleja el estado final: servicios, puertos (incluidos Grafana/Prometheus), cómo levantar y **cómo probar** (sección nueva: credenciales semilla + `docs/quien-juega.http`).
    - [x] Una `explicacion_*` por cada tema que se vaya a defender en el coloquio: se agregaron `explicacion_observabilidad.md` (T-50/T-51, no existía) y `explicacion_frontend.md` (T-60 a T-65, no existía).
  - **Terminada cuando:** alguien ajeno al grupo levanta y entiende el proyecto leyendo solo el README y `/docs`. ✅ Verificado el 2026-09-30: de paso se corrigieron partes del README que habían quedado desactualizadas por tareas anteriores (conteo de contenedores, referencia a T-42 "todavía no" pese a estar hecha hace rato, tabla de puertos sin Prometheus/Grafana/Loki/frontend).

- [ ] **T-73 · Ensayo del coloquio** — transversal · depende de: T-71, T-72 · todos · [plan](plan_73_ensayo_coloquio.md)
  - **Qué:** guion de la demo, reparto de quién explica qué, y ensayo completo sobre un stack levantado de cero.
  - **Por qué:** la nota depende del coloquio, y hay partes (apertura del circuito, correlación de logs) que hay que saber mostrar en vivo.
  - **Afecta:** `docs/`.
  - **Requisitos:**
    - [x] Guion con el orden de la demo y qué muestra cada integrante — [docs/guion_coloquio.md](guion_coloquio.md), con reparto propuesto por carril (A/B/C), ajustable.
    - [x] Cada requisito obligatorio del enunciado tiene un momento donde se muestra funcionando — los 8 del enunciado + Resilience4J, mapeados 1 a 1 en el guion.
    - [x] La demostración del Circuit Breaker está ensayada — son los mismos pasos verificados en Docker para T-31 y R-11, ya reproducidos más de una vez en esta sesión.
    - [ ] Ensayo completo hecho al menos una vez de punta a punta — **pendiente, no lo puedo hacer yo**: "el grupo corre la demo" son los tres integrantes practicando juntos, repartiéndose el guion propuesto (o el que decidan) y cronometrándose. El guion tiene una tabla al final para anotar la fecha y el tiempo real una vez lo hagan.
    - [ ] Esta planificación queda con todas las tareas marcadas (estaba en T-72, pero dependía de esta tarea, que a su vez depende de T-72).
  - **Terminada cuando:** el grupo corre la demo entera sin improvisar y dentro del tiempo previsto. **No cerrada todavía** — queda el ensayo real de los tres, ver el requisito de arriba.

- [ ] **T-74 · Kafka confiable en un arranque limpio** — `docker-compose.yml`, `alert-dispatcher-service` · depende de: T-23, T-30 · carril B
  - **Qué:** que `match-events` exista con sus 3 particiones antes de que arranque el consumer, y que Kafka guarde sus datos en el volumen `kafka-data`.
  - **Por qué:** resuelve R-14 y R-15. Hoy, después de cada `docker compose down` + `up`, el primer evento puede tardar hasta 5 minutos en llegar como alerta, que es justo lo que se muestra en vivo en el coloquio. Conviene hacerla antes del ensayo de T-73.
  - **Afecta:** `docker-compose.yml` (servicio `kafka`), configuración de Kafka de `alert-dispatcher-service`, `docs/explicacion_docker_compose.md`, `docs/explicacion_mensajeria_kafka.md` y el gotcha de Kafka en `CLAUDE.md`.
  - **Requisitos:**
    - [ ] Con volúmenes limpios, el consumer arranca con las 3 particiones de `match-events` asignadas, levante primero el servicio que levante.
    - [ ] Los tópicos y los offsets del consumer sobreviven a `docker compose down` + `up` (sin `-v`).
    - [ ] La documentación dice dónde persiste Kafka y quién crea el tópico.
  - **Terminada cuando:** tras `docker compose down -v && docker compose up -d --build`, el primer evento ingestado le llega al fan en segundos y no en minutos; y tras `docker compose down && docker compose up -d`, `match-events` sigue existiendo con 3 particiones.

---

## 7. Resumen de avance

| Fase | Tareas | Completadas |
|---|---|---|
| 0 — Cimientos | 3 | 3 |
| 1 — Núcleo de dominio | 12 | 12 |
| 2 — Integración y seguridad | 3 | 3 |
| 3 — Requisitos transversales | 2 | 2 |
| 4 — Frontend | 6 | 6 |
| 5 — Cierre y entrega | 5 | 3 |
| **Total** | **31** | **29** |

> T-66 (Panel Principal) es una tarea **propuesta**, no forma parte del recuento de 31: se agrega a la tabla si el grupo decide tomarla.

Actualizar esta tabla al cerrar cada tarea.
