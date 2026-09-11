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

Foto al momento de escribir esta planificación (commit `7e1689e`).

| Componente | Estado | Detalle |
|---|---|---|
| `eureka-server` | ✅ Hecho | App + `application.yaml` + Dockerfile. Sin verificar en ejecución. |
| `cloud-gateway` | 🟡 Parcial | Rutas declaradas hacia los 3 MS. Sin seguridad, sin CORS. Único con Actuator. |
| `docker-compose.yml` | ✅ Hecho | Postgres, Mongo, Kafka (KRaft), Kafka UI, los 5 servicios Spring, red y volúmenes. Sin verificar. |
| `fan-identity-service` | 🔴 Scaffolding | Solo la clase `@SpringBootApplication`. `application.yaml` casi vacío. Sin entidades, sin endpoints, sin seguridad configurada. |
| `match-ingestion-service` | 🔴 Scaffolding | Solo la clase `@SpringBootApplication`. Sin documentos Mongo, sin endpoints, sin productor Kafka. |
| `alert-dispatcher-service` | 🟡 Parcial | Feign client + Circuit Breaker configurados (paths provisorios). Sin consumer Kafka, sin orquestación. |
| `frontend-app` | 🔴 No existe | La carpeta no está en el repo. Los docs ya lo marcan como pendiente (T-00); se crea en T-60. |
| Seguridad (JWT) | 🔴 Falta | Solo la dependencia en el `pom` de fan-identity. |
| Mensajería (Kafka) | 🔴 Falta | Infra levantada, sin producer ni consumer ni esquema de evento. |
| Logs centralizados | 🔴 Falta | **Requisito obligatorio ausente del diseño del grupo.** |
| Telemetría de métricas | 🔴 Falta | **Requisito obligatorio ausente del diseño del grupo.** |
| Resilience4J (extra) | 🟡 Parcial | Circuito configurado, fallback devuelve lista vacía con un `TODO`. |

### Cobertura de los requisitos obligatorios del enunciado

| Requisito del enunciado | Estado | Se resuelve en |
|---|---|---|
| ≥ 3 microservicios Spring Boot + 1 frontend | 🟡 | Fase 1 + Fase 4 |
| 1 base SQL + 1 NoSQL, 1 microservicio ≠ 2 bases | 🟡 | T-10, T-20 |
| Mensajería asincrónica | 🔴 | T-23, T-30 |
| Spring Cloud Gateway + Eureka | 🟡 | T-02 (verificación) |
| Ejecución en contenedores Docker | ✅ | T-02 (verificación) |
| Seguridad | 🔴 | T-11, T-40, T-41 |
| Almacén centralizado de logs | 🔴 | T-51 |
| Telemetría de métricas de rendimiento | 🔴 | T-50 |
| 1 característica extra (Resilience4J) | 🟡 | T-31 |

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

- [ ] **R-1 · Dos requisitos obligatorios no figuran en la resolución propuesta:** almacén centralizado de logs y telemetría de métricas. Es el hueco más grande del diseño actual. Cubierto por T-50 y T-51.
- [ ] **R-2 · Stack muy reciente sin evidencia de que arranque:** Spring Boot 4.1 + Java 25 + Spring Cloud 2025.1.2 + `spring-cloud-starter-gateway-server-webmvc`. Nadie verificó todavía un `docker compose up --build` exitoso. **Es el riesgo número uno del proyecto** porque bloquea todo lo demás: por eso T-02 existe y va antes que cualquier código de negocio.
- [ ] **R-3 · `frontend-app/` está documentado en README y CLAUDE pero no existe.** Los docs ya lo marcan como pendiente (T-00 ✅); falta crearlo en T-60.
- [ ] **R-4 · Los `application.yaml` de los 3 microservicios están casi vacíos** (solo `spring.application.name`). Toda la configuración vive en variables de entorno de `docker-compose.yml`, así que hoy **ningún microservicio se puede correr con `mvn spring-boot:run`** para desarrollar. Se corrige en T-02.
- [ ] **R-5 · `fan-identity-service` tiene `spring-boot-starter-security` sin configurar.** Al arrancar, Spring Security protege todo con basic auth y una contraseña random en el log: cualquier request vía gateway daría 401. Se corrige en T-11.
- [ ] **R-6 · El path del Feign client es provisorio** (`GET /api/fans/subscribers/{teamId}`). Se congela en T-01.
- [ ] **R-7 · Actuator solo está en `cloud-gateway`**, y los healthchecks de Docker son TCP contra el puerto. Se corrige en T-50.
- [x] **R-8 · El mapa del proyecto en README y CLAUDE decía `tp-nube-deportes/`**, pero la carpeta real es `TP-DAN`. Corregido en T-00.
- [x] **R-9 · Los mockups se llamaban `page_3_image_1_v2.png`**, nombres heredados de la exportación del informe. Movidos a `docs/assets/` con nombres descriptivos y enlaces de `resolucion_propuesta.md` actualizados en T-00.

---

## 4. Organización de la documentación

Estructura propuesta para `/docs` (mantiene tus reglas, agrega solo una carpeta de imágenes):

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

**Reparto de responsabilidades entre `README.md` y `CLAUDE.md`** (hoy duplican problema, alcance, roles, mapa del proyecto, puertos y comandos Docker):

| | `README.md` | `CLAUDE.md` |
|---|---|---|
| Audiencia | Persona que quiere entender o ejecutar el proyecto | IA que trabaja sobre el código |
| Contenido | Problema, alcance, arquitectura, roles, tecnologías, cómo levantar, puertos, índice de `/docs` | Metodología de trabajo, convenciones de código, decisiones de arquitectura vigentes, gotchas técnicos, qué **no** hacer |
| Regla | Es la fuente sobre el dominio | No repite el dominio: enlaza al README |

Lo que hoy está en `CLAUDE.md` y debería moverse:
- Problema, alcance, roles, mapa del proyecto, tabla de puertos, comandos Docker → **ya están en README**, se borran de CLAUDE.
- "Scaffolding preparado para la llamada…" y "Puntos a verificar antes de la entrega" → **se quedan en CLAUDE** (son gotchas para quien escriba código), resumidos.
- El detalle largo del Circuit Breaker → `docs/explicacion_circuit_breaker.md` cuando se implemente (T-31).

Lo que hay que **agregar** a `CLAUDE.md`:
- La metodología de trabajo (planificación global → `plan_*` → implementación).
- Criterios de código para el nivel del TP: código simple y legible por encima de abstracciones; sin capas ni patrones que el TP no pida; comentarios en español solo donde el *por qué* no sea obvio; DTOs como `record`; nombres de dominio en inglés y comentarios en español (como ya viene el código).
- Prohibiciones explícitas: no inventar endpoints fuera del contrato de T-01, no agregar dependencias sin justificarlo, no tocar `docker-compose.yml` sin actualizar `docs/explicacion_docker_compose.md`.

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
  - **Qué:** aplicar la estructura de la sección 4: separar responsabilidades de README y CLAUDE, eliminar la duplicación, crear `docs/assets/`, corregir las inconsistencias R-3, R-8 y R-9.
  - **Por qué:** los tres integrantes van a trabajar con IA sobre el mismo repo; si CLAUDE.md no fija la metodología y las convenciones, cada IA va a generar código con criterios distintos.
  - **Afecta:** `README.md`, `CLAUDE.md`, `docs/`.
  - **Requisitos:**
    - [x] `README.md` conserva dominio, arquitectura, tecnologías, cómo levantar y puertos; agrega un índice de `/docs`.
    - [x] `CLAUDE.md` deja de repetir el dominio y suma metodología de trabajo + criterios de código + prohibiciones.
    - [x] Ambos dejan de mencionar `frontend-app/` como existente (se marca como pendiente) y usan el nombre real de la carpeta raíz.
    - [x] Mockups movidos a `docs/assets/` con nombres descriptivos y enlaces de `resolucion_propuesta.md` actualizados.
  - **Terminada cuando:** un integrante que no escribió los docs puede leer solo el README y levantar el proyecto, y CLAUDE.md no repite nada que ya esté en el README.

- [ ] **T-01 · Congelar el contrato de APIs y el esquema del evento Kafka** — transversal · depende de: D-1, D-2, D-3, D-6 · todos
  - **Qué:** escribir `docs/explicacion_contratos_api.md` con **todos** los endpoints (método, path, request, response, códigos de error, si es público/autenticado/interno) y el JSON del evento que viaja por Kafka, incluyendo nombre del tópico y clave de partición.
  - **Por qué:** es lo único que permite que los tres carriles avancen en paralelo sin pisarse. Sin esto, el frontend no sabe qué consumir y alert-dispatcher no sabe qué pedirle a fan-identity (hoy el Feign client apunta a un path inventado, R-6).
  - **Afecta:** ningún código todavía; condiciona todas las tareas de Fase 1 y 4.
  - **Requisitos:**
    - [ ] Endpoints públicos de auth (`/api/fans/auth/**`).
    - [ ] Endpoints del fan autenticado: suscripciones y notificaciones.
    - [ ] Endpoint de suscriptores por equipo que consume alert-dispatcher (reemplaza el path provisorio).
    - [ ] Endpoint interno de persistencia de notificación (callback de D-3), bajo un prefijo **fuera** de `/api/**` para que el gateway no lo exponga.
    - [ ] Catálogo y eventos de `match-ingestion` (según D-1).
    - [ ] Endpoint de ingesta de eventos (el que usa el admin para simular).
    - [ ] Esquema del evento Kafka: nombre del tópico, campos, tipos, ejemplo completo.
    - [ ] Formato único de error para todos los servicios.
  - **Terminada cuando:** cada carril puede implementar su parte sin preguntarle nada a los otros dos.

- [ ] **T-02 · Configuración base ejecutable y prueba de humo del stack** — los 5 servicios Spring · depende de: — · todos
  - **Qué:** completar los `application.yaml` de los 3 microservicios (puerto, cliente Eureka, datasource/Mongo/Kafka con valores por defecto para correr local), y verificar de punta a punta que `docker compose up --build` levanta todo y los 5 servicios aparecen registrados en Eureka.
  - **Por qué:** R-2 y R-4. Hoy la configuración vive solo en variables de entorno de Docker, así que nadie puede correr un microservicio suelto para desarrollar; y el stack (Boot 4.1 / Java 25 / Spring Cloud 2025.1.2) nunca se ejecutó. Si algo de esto no compila, bloquea el proyecto entero y hay que saberlo ahora, no en la semana de entrega.
  - **Afecta:** `*/src/main/resources/application.yaml`, eventualmente `pom.xml` y `docker-compose.yml`.
  - **Requisitos:**
    - [ ] Cada microservicio define `server.port` y `eureka.client.service-url.defaultZone` con default local, sobreescribible por variable de entorno (las de `docker-compose.yml` siguen funcionando).
    - [ ] `mvn spring-boot:run` levanta cada microservicio contra la infra local sin editar archivos.
    - [ ] `docker compose up --build` completa sin errores y todos los contenedores quedan `healthy`.
    - [ ] El dashboard de Eureka en `:8761` muestra los 4 clientes registrados.
    - [ ] Se confirma que el namespace `spring.cloud.gateway.server.webmvc.routes` es el correcto para esta versión (ver `CLAUDE.md`).
    - [ ] Cualquier problema de versiones se documenta en `CLAUDE.md`.
  - **Terminada cuando:** cualquier integrante clona el repo, corre `docker compose up --build` y ve los 4 servicios en Eureka.

### Fase 1 — Núcleo de dominio (paralelizable en 3 carriles)

#### Carril A — `fan-identity-service`

- [ ] **T-10 · Modelo de datos, catálogo y persistencia** — `fan-identity-service` · depende de: T-01, T-02 · carril A
  - **Qué:** entidades JPA `Fan`, `Subscription`, `League` y `Team` (+ rol, según D-5), repositorios y datos semilla (ligas y equipos reales, algunos fans y un admin).
  - **Por qué:** es la base de todo el carril A. Por D-1 el catálogo deportivo vive acá, así que la suscripción puede tener FK real al equipo: es la justificación de haber elegido PostgreSQL.
  - **Afecta:** `.../fan_identity_service/` (paquetes de dominio y repositorio), `application.yaml`.
  - **Requisitos:**
    - [ ] `Fan` con email único, contraseña hasheada (BCrypt) y rol.
    - [ ] `League` y `Team`, con `Team` perteneciendo a una `League`.
    - [ ] `Subscription` con FK a `Fan` y a `Team`, y restricción de unicidad para no duplicar suscripciones.
    - [ ] El identificador de `Team` es el mismo `teamId` que viaja en los eventos de Kafka y que guarda `match-ingestion-service`.
    - [ ] Datos semilla cargados al arrancar, idempotentes (no duplican al reiniciar).
    - [ ] Las tablas se crean solas contra el Postgres del compose.
  - **Terminada cuando:** el servicio arranca contra Postgres, crea el esquema y los datos semilla (ligas, equipos, fans, admin) se ven consultando la base.

- [ ] **T-11 · Registro, login y emisión de JWT** — `fan-identity-service` · depende de: T-10 · carril A
  - **Qué:** endpoints públicos de registro y login, hash de contraseñas, emisión de JWT firmado, y la `SecurityFilterChain` del servicio.
  - **Por qué:** cubre el requisito obligatorio de seguridad y el punto 3.1 de la resolución. Además desactiva el basic auth por defecto que hoy rompería todo (R-5).
  - **Afecta:** `.../fan_identity_service/` (auth, security, config).
  - **Requisitos:**
    - [ ] Registro valida email único y devuelve error claro si ya existe.
    - [ ] Login devuelve JWT con el id del fan y su rol en los claims.
    - [ ] Contraseñas hasheadas con BCrypt, nunca en texto plano ni en logs.
    - [ ] Rutas de auth públicas, el resto autenticado.
    - [ ] La clave de firma se lee de configuración (variable de entorno), con default solo para desarrollo local.
  - **Terminada cuando:** se puede registrar un fan, loguearlo, obtener un JWT y usarlo para acceder a un endpoint protegido del servicio.

- [ ] **T-12 · Suscripciones del fan** — `fan-identity-service` · depende de: T-11 · carril A
  - **Qué:** seguir un equipo, dejar de seguirlo y listar las suscripciones del fan autenticado.
  - **Por qué:** es la funcionalidad central del rol Fan (punto 3.2) y lo que alimenta el fan-out de alertas.
  - **Afecta:** `.../fan_identity_service/` (subscription).
  - **Requisitos:**
    - [ ] Los tres endpoints operan **siempre** sobre el fan del JWT, nunca sobre un id que venga en el body.
    - [ ] Seguir dos veces el mismo equipo no duplica ni falla con error 500.
    - [ ] Seguir un equipo inexistente se rechaza con un error claro (la FK a `Team` lo garantiza).
    - [ ] Dejar de seguir algo no suscripto devuelve un código coherente, no una excepción.
    - [ ] El listado devuelve el nombre del equipo y su liga, resueltos por join (sin desnormalizar).
  - **Terminada cuando:** con un JWT válido se puede seguir, listar y dejar de seguir equipos, y los cambios persisten tras reiniciar el servicio.

- [ ] **T-13 · Endpoint de suscriptores por equipo** — `fan-identity-service` · depende de: T-12 · carril A
  - **Qué:** el endpoint que consume `alert-dispatcher-service` para saber a quién notificar, según el contrato de T-01.
  - **Por qué:** es la pieza que cierra el fan-out y la llamada que protege el Circuit Breaker.
  - **Afecta:** `.../fan_identity_service/` (controlador interno).
  - **Requisitos:**
    - [ ] Devuelve la lista de fans suscriptos a un equipo, con los campos exactos del contrato.
    - [ ] Equipo sin suscriptos devuelve lista vacía, no 404.
    - [ ] Queda fuera del prefijo que el gateway expone (no alcanzable desde el host).
  - **Terminada cuando:** `alert-dispatcher-service` obtiene la lista real vía Feign dentro de la red Docker y el contrato coincide con `FanSubscriberDTO`.

- [ ] **T-14 · Historial de notificaciones** — `fan-identity-service` · depende de: T-12 · carril A
  - **Qué:** entidad `Notification`, endpoint **interno** de alta (el callback de D-3) y endpoint del fan para listar su historial.
  - **Por qué:** el Fan tiene que poder ver su historial de alertas y alert-dispatcher no tiene base propia.
  - **Afecta:** `.../fan_identity_service/` (notification).
  - **Requisitos:**
    - [ ] `Notification` guarda a qué fan pertenece, el texto, el evento que la originó y la fecha.
    - [ ] Alta interna, fuera del prefijo público del gateway.
    - [ ] El listado devuelve solo las notificaciones del fan del JWT, ordenadas de más nueva a más vieja.
  - **Terminada cuando:** insertando una notificación por el endpoint interno, el fan la ve en su historial.

- [ ] **T-15 · Endpoints de catálogo de ligas y equipos** — `fan-identity-service`, `cloud-gateway` · depende de: T-10, T-11 · carril A
  - **Qué:** endpoints de lectura para listar ligas y buscar equipos por nombre, más la ruta `/api/catalog/**` en el gateway.
  - **Por qué:** el Fan "busca equipos o ligas en el catálogo" antes de suscribirse, y por D-1 el catálogo vive en este servicio. Va bajo su propio prefijo porque servirlo desde `/api/fans/**` sería confuso.
  - **Afecta:** `.../fan_identity_service/` (controlador de catálogo), `cloud-gateway/application.yaml`.
  - **Requisitos:**
    - [ ] Listado de ligas y listado de equipos, con filtro por nombre.
    - [ ] Los equipos devuelven el `teamId` que acepta T-12 para suscribirse.
    - [ ] La ruta `/api/catalog/**` del gateway enruta a `fan-identity-service` vía Eureka.
  - **Terminada cuando:** vía gateway se puede buscar un equipo por nombre y usar el `teamId` devuelto para suscribirse.

#### Carril B — `match-ingestion-service` y `alert-dispatcher-service`

- [ ] **T-20 · Modelo de documentos de partidos y eventos** — `match-ingestion-service` · depende de: T-01, T-02 · carril B
  - **Qué:** documentos Mongo para partido y evento deportivo, con repositorios y datos semilla (un par de partidos).
  - **Por qué:** es la justificación de haber elegido MongoDB: los eventos son heterogéneos y un esquema de documentos flexible encaja mejor que uno relacional. Por D-1 el catálogo de ligas/equipos **no** vive acá.
  - **Afecta:** `.../match_ingestion_service/` (dominio y repositorios).
  - **Requisitos:**
    - [ ] El documento de evento soporta tipos distintos (gol, tarjeta, fin de partido) sin campos obligatorios que solo apliquen a uno.
    - [ ] Partido y evento referencian `teamId` como string: el mismo identificador que usa el catálogo de T-10, sin FK entre servicios.
    - [ ] Semilla idempotente.
  - **Terminada cuando:** el servicio arranca contra Mongo y los partidos semilla se pueden consultar desde la base.

- [ ] **T-21 · Endpoint de ingesta de eventos** — `match-ingestion-service` · depende de: T-20 · carril B
  - **Qué:** el endpoint que recibe un evento deportivo de un proveedor externo, lo valida y lo persiste. Es también el que usa el admin para simular.
  - **Por qué:** es la puerta de entrada de datos del sistema y el disparador de todo el flujo de punta a punta.
  - **Afecta:** `.../match_ingestion_service/` (controlador y servicio de ingesta).
  - **Requisitos:**
    - [ ] Valida el formato y rechaza eventos incompletos con un error claro, sin persistirlos.
    - [ ] Rechaza eventos cuyo partido no exista en su propia base. **No** valida el `teamId` contra el catálogo: por D-1 vive en otro servicio y no vale la pena otra llamada entre servicios.
    - [ ] Persiste el evento y devuelve su identificador.
  - **Terminada cuando:** un POST con un evento válido lo deja guardado en Mongo, y uno inválido devuelve error sin ensuciar la base.

- [ ] **T-22 · Consulta de partidos y eventos** — `match-ingestion-service` · depende de: T-20 · carril B
  - **Qué:** endpoints de lectura para que el frontend liste los partidos y el historial de eventos de un partido. El catálogo de ligas y equipos **no** está acá: es T-15.
  - **Por qué:** alimenta el muro de eventos en vivo del panel principal (ver el mockup en `docs/assets/mockup_panel_principal.png`).
  - **Afecta:** `.../match_ingestion_service/` (controlador de consulta).
  - **Requisitos:**
    - [ ] Listado de partidos, con filtro por equipo.
    - [ ] Eventos de un partido ordenados cronológicamente.
    - [ ] Las respuestas incluyen el `teamId` de cada equipo participante para que el frontend pueda cruzarlo con el catálogo.
  - **Terminada cuando:** vía gateway se pueden listar partidos y ver los eventos de uno en orden cronológico.

- [ ] **T-23 · Publicación del evento en Kafka** — `match-ingestion-service` · depende de: T-21 · carril B
  - **Qué:** tras persistir un evento, publicarlo en el tópico definido en T-01, con el esquema acordado.
  - **Por qué:** es el requisito obligatorio de mensajería asincrónica y desacopla la ingesta del despacho de alertas.
  - **Afecta:** `.../match_ingestion_service/` (productor Kafka), `application.yaml`.
  - **Requisitos:**
    - [ ] El tópico se declara explícitamente (bean `NewTopic`), sin depender de la auto-creación del broker.
    - [ ] El mensaje respeta exactamente el esquema de T-01 y usa `teamId` como clave.
    - [ ] Si Kafka está caído, el evento igual queda persistido y el fallo se loguea; no se pierde el POST.
  - **Terminada cuando:** al ingestar un evento, el mensaje se ve en Kafka UI (`:8090`) en el tópico correcto.

- [ ] **T-30 · Consumer Kafka y orquestación del fan-out** — `alert-dispatcher-service` · depende de: T-23, T-13, T-14 · carril B
  - **Qué:** consumir el tópico, resolver los suscriptos con `FanIdentityLookupService` y persistir una notificación por fan vía el callback a fan-identity.
  - **Por qué:** es el corazón del sistema: convierte un evento deportivo en alertas personalizadas.
  - **Afecta:** `.../alert_dispatcher_service/` (listener, servicio de despacho, cliente del callback).
  - **Requisitos:**
    - [ ] El listener deserializa el evento según el esquema de T-01.
    - [ ] Se genera una notificación por cada fan suscripto, con un texto legible construido a partir del evento.
    - [ ] Un evento sin suscriptos se procesa sin error y se loguea.
    - [ ] Un fallo al notificar a un fan no impide notificar a los demás.
  - **Terminada cuando:** al ingestar un evento de un equipo que un fan sigue, ese fan ve la notificación en su historial.

- [ ] **T-31 · Cerrar el Circuit Breaker y exponer su estado** — `alert-dispatcher-service` · depende de: T-30 · carril B
  - **Qué:** reemplazar el fallback vacío por uno real (el `TODO` de `FanIdentityLookupService`) y exponer el estado del despachador según D-6.
  - **Por qué:** Resilience4J es la característica extra elegida y hoy el fallback descarta eventos en silencio, que es justamente lo que no hay que mostrar en el coloquio.
  - **Afecta:** `.../alert_dispatcher_service/` (service, config, controlador de estado).
  - **Requisitos:**
    - [ ] El fallback deja registro de los eventos no despachados en vez de descartarlos.
    - [ ] Endpoint de estado con últimos eventos procesados y estado del circuito.
    - [ ] Se documenta en `docs/explicacion_circuit_breaker.md` cómo demostrar la apertura del circuito (bajar fan-identity y mostrar el comportamiento).
  - **Terminada cuando:** apagando `fan-identity-service` e ingestando eventos, el circuito abre, alert-dispatcher sigue respondiendo y el comportamiento es reproducible con los pasos documentados.

### Fase 2 — Integración y seguridad (los tres juntos)

- [ ] **T-40 · Seguridad y CORS en el gateway** — `cloud-gateway` · depende de: T-11, D-2 · todos
  - **Qué:** filtro que valida el JWT en el gateway, lista de rutas públicas y configuración de CORS para el frontend.
  - **Por qué:** el gateway es el único punto de entrada; validar ahí evita repetir seguridad en cada microservicio. Sin CORS, el frontend no puede consumir nada.
  - **Afecta:** `cloud-gateway` (config y filtro), `pom.xml`.
  - **Requisitos:**
    - [ ] Request sin token o con token inválido a una ruta protegida devuelve 401 sin llegar al microservicio.
    - [ ] Rutas de auth accesibles sin token.
    - [ ] El gateway propaga el token al microservicio de destino.
    - [ ] CORS habilitado para el origen del frontend.
  - **Terminada cuando:** desde el navegador se puede loguear y consumir endpoints protegidos, y un token adulterado se rechaza en el gateway.

- [ ] **T-41 · Blindar los endpoints internos** — `cloud-gateway`, `fan-identity-service` · depende de: T-40, T-13, T-14 · todos
  - **Qué:** verificar que los endpoints internos (suscriptores por equipo y callback de notificaciones) no sean alcanzables desde el host, y protegerlos entre servicios.
  - **Por qué:** el callback de notificaciones escribe en la base sin JWT de usuario; si queda expuesto, cualquiera puede inyectar notificaciones a cualquier fan.
  - **Afecta:** rutas del gateway, `SecurityFilterChain` de fan-identity, `docker-compose.yml`.
  - **Requisitos:**
    - [ ] Ningún endpoint interno responde desde `localhost:8080`.
    - [ ] La llamada interna entre servicios se autentica (secreto compartido por variable de entorno).
    - [ ] El secreto no queda hardcodeado en el código.
  - **Terminada cuando:** los endpoints internos funcionan entre contenedores y devuelven 404/401 desde afuera.

- [ ] **T-42 · Verificación del flujo de punta a punta** — transversal · depende de: T-30, T-40, T-41 · todos
  - **Qué:** ejercitar el recorrido completo: registro → login → buscar equipo → suscribirse → ingestar evento → ver la notificación.
  - **Por qué:** es exactamente la demo del coloquio; hay que saber que funciona antes de construir el frontend encima.
  - **Afecta:** ningún código nuevo; puede destapar correcciones en tareas previas.
  - **Requisitos:**
    - [ ] El recorrido completo funciona pasando siempre por el gateway.
    - [ ] Quedan registrados los pasos exactos para reproducirlo.
    - [ ] Los errores encontrados se corrigen o se anotan como tareas nuevas en este documento.
  - **Terminada cuando:** el flujo se puede reproducir desde cero con `docker compose down -v && docker compose up --build`.

### Fase 3 — Requisitos transversales del enunciado

- [ ] **T-50 · Telemetría de métricas de rendimiento** — los 5 servicios Spring + compose · depende de: T-02, D-4 · carril C
  - **Qué:** Actuator + Micrometer con registro Prometheus en los 5 servicios, Prometheus y Grafana en el compose, y un dashboard con las métricas clave.
  - **Por qué:** **requisito obligatorio del enunciado** hoy ausente. De paso permite migrar los healthchecks TCP a `/actuator/health` (R-7).
  - **Afecta:** los 5 `pom.xml` y `application.yaml`, `docker-compose.yml`, configuración de Prometheus y Grafana.
  - **Requisitos:**
    - [ ] Los 5 servicios exponen `/actuator/prometheus`.
    - [ ] Prometheus scrapea a los 5 y Grafana los grafica.
    - [ ] El dashboard muestra al menos: latencia y volumen de requests HTTP, uso de memoria/JVM y estado del circuito de Resilience4J.
    - [ ] Los healthchecks de `docker-compose.yml` pasan a `/actuator/health`.
    - [ ] Los endpoints de Actuator no quedan expuestos públicamente por el gateway.
  - **Terminada cuando:** generando tráfico con el flujo de T-42, las métricas se mueven en el dashboard de Grafana.

- [ ] **T-51 · Almacén centralizado de logs** — transversal · depende de: T-50, D-4 · carril C
  - **Qué:** Loki + Promtail en el compose, logs de los 5 servicios en formato consistente y un identificador de correlación que permita seguir un evento a través de los servicios.
  - **Por qué:** **requisito obligatorio del enunciado** hoy ausente. Reutiliza el Grafana de T-50.
  - **Afecta:** `docker-compose.yml`, configuración de logging de los servicios.
  - **Requisitos:**
    - [ ] Promtail recolecta los logs de todos los contenedores y los envía a Loki.
    - [ ] Desde Grafana se pueden filtrar logs por servicio y por nivel.
    - [ ] Un identificador de correlación viaja desde la ingesta hasta la notificación y aparece en los logs de los tres microservicios.
    - [ ] Ninguna contraseña, token ni dato sensible aparece en los logs.
  - **Terminada cuando:** disparando un evento se puede seguir su recorrido completo en Grafana filtrando por el identificador de correlación.

### Fase 4 — Frontend

- [ ] **T-60 · Scaffolding de `frontend-app`** — `frontend-app/` · depende de: T-02 · carril C
  - **Qué:** crear el proyecto Next.js (Pages Router), su Dockerfile y su servicio en `docker-compose.yml`, apuntando al gateway.
  - **Por qué:** es el cuarto componente que exige el enunciado y hoy la carpeta no existe (R-3). Puede arrancar en paralelo con la Fase 1.
  - **Afecta:** `frontend-app/`, `docker-compose.yml`, `.gitignore`.
  - **Requisitos:**
    - [ ] Proyecto Next.js con Pages Router que levanta en desarrollo.
    - [ ] La URL del gateway se lee de variable de entorno, nunca hardcodeada.
    - [ ] Dockerfile funcional y servicio agregado al compose.
    - [ ] Una página inicial que confirme que el contenedor responde.
  - **Terminada cuando:** `docker compose up --build` levanta también el frontend y se accede desde el navegador.

- [ ] **T-61 · Capa de acceso a la API y sesión** — `frontend-app/` · depende de: T-60, T-01 · carril C
  - **Qué:** un único módulo cliente que hable con el gateway, guarde el JWT, lo adjunte a cada request y maneje el 401.
  - **Por qué:** centralizar esto evita repetir `fetch` y manejo de token en cada pantalla.
  - **Afecta:** `frontend-app/` (servicios y contexto de sesión).
  - **Requisitos:**
    - [ ] Todas las llamadas pasan por este módulo y por el gateway, nunca directo a un microservicio.
    - [ ] El token se adjunta automáticamente y el 401 redirige al login.
    - [ ] Rutas privadas inaccesibles sin sesión.
  - **Terminada cuando:** ninguna pantalla hace `fetch` por su cuenta ni conoce puertos de microservicios.

- [ ] **T-62 · Pantallas de registro y login** — `frontend-app/` · depende de: T-61, T-11 · carril C
  - **Qué:** las pantallas de acceso del fan, siguiendo el mockup de la resolución.
  - **Por qué:** es la puerta de entrada de la aplicación y de la demo.
  - **Afecta:** `frontend-app/` (páginas de auth).
  - **Requisitos:**
    - [ ] Registro y login funcionales contra el gateway.
    - [ ] Errores del backend (email duplicado, credenciales inválidas) mostrados de forma legible.
    - [ ] Tras loguear, redirige al panel principal.
  - **Terminada cuando:** un usuario nuevo se registra, inicia sesión y entra al panel sin tocar la consola.

- [ ] **T-63 · Catálogo y mis suscripciones** — `frontend-app/` · depende de: T-62, T-12, T-15 · carril C
  - **Qué:** pantalla de búsqueda de equipos/ligas con seguir/dejar de seguir, y pantalla de suscripciones, según los mockups.
  - **Por qué:** es la funcionalidad principal del rol Fan.
  - **Afecta:** `frontend-app/` (páginas de catálogo y suscripciones).
  - **Requisitos:**
    - [ ] Búsqueda de equipos por nombre.
    - [ ] Seguir/dejar de seguir refleja el cambio sin recargar la página.
    - [ ] El listado de suscripciones muestra nombres de equipo, no identificadores.
  - **Terminada cuando:** un fan busca un equipo, lo sigue y lo ve aparecer en sus suscripciones.

- [ ] **T-64 · Centro de alertas** — `frontend-app/` · depende de: T-62, T-14 · carril C
  - **Qué:** pantalla del historial de notificaciones del fan, según el mockup.
  - **Por qué:** cierra el recorrido del Fan y es donde se ve el resultado del flujo asincrónico.
  - **Afecta:** `frontend-app/` (página de alertas).
  - **Requisitos:**
    - [ ] Notificaciones ordenadas de más nueva a más vieja con su fecha.
    - [ ] Estado vacío claro cuando el fan todavía no tiene alertas.
    - [ ] Se puede refrescar para ver alertas nuevas.
  - **Terminada cuando:** con el fan logueado y un evento ingestado, la alerta aparece en la pantalla.

- [ ] **T-65 · Panel de administrador** — `frontend-app/` · depende de: T-63, T-21, T-31, D-5 · carril C
  - **Qué:** pantalla de admin con el formulario para simular la carga de un evento deportivo y enlaces/estado de monitoreo.
  - **Por qué:** es el rol Administrador de la resolución y la forma de disparar la demo en vivo durante el coloquio.
  - **Afecta:** `frontend-app/` (páginas de admin).
  - **Requisitos:**
    - [ ] Solo accesible con rol admin.
    - [ ] Formulario de simulación de evento que llama al endpoint de ingesta.
    - [ ] Estado del despachador y enlaces a Eureka, Kafka UI y Grafana.
  - **Terminada cuando:** el admin dispara un evento desde la UI y el fan suscripto ve la alerta en su centro de alertas.

### Fase 5 — Cierre y entrega

- [ ] **T-70 · Pruebas automatizadas mínimas** — transversal · depende de: T-42 · todos
  - **Qué:** tests de las reglas de negocio que más duelen si se rompen, más un test de contexto por servicio.
  - **Por qué:** el enunciado no exige tests, así que el objetivo es cubrir lo crítico sin invertir tiempo desproporcionado.
  - **Afecta:** `src/test/java` de los microservicios.
  - **Requisitos:**
    - [ ] Cada servicio levanta su contexto en un test (detecta configuración rota).
    - [ ] fan-identity: tests de registro/login y de no duplicar suscripciones.
    - [ ] match-ingestion: test de rechazo de evento inválido.
    - [ ] alert-dispatcher: test del fallback del Circuit Breaker.
    - [ ] `mvn test` pasa en los 5 servicios.
  - **Terminada cuando:** `mvn test` está en verde en todo el backend.

- [ ] **T-71 · Colección de requests reproducible** — `docs/` · depende de: T-42 · todos
  - **Qué:** archivo `.http` (o colección equivalente) con todo el flujo en orden, ejecutable desde el editor.
  - **Por qué:** sirve para probar sin frontend y es el plan B si algo falla en la demo del coloquio.
  - **Afecta:** `docs/`.
  - **Requisitos:**
    - [ ] Cubre el recorrido completo de T-42 en orden.
    - [ ] El token se reutiliza entre requests sin copiar y pegar a mano.
  - **Terminada cuando:** ejecutando la colección de arriba a abajo sobre un stack recién levantado, todas las respuestas son las esperadas.

- [ ] **T-72 · Documentación final** — `docs/`, `README.md` · depende de: T-50, T-51, T-65 · todos
  - **Qué:** actualizar README y escribir las `explicacion_*` que falten (arquitectura, seguridad, mensajería, observabilidad).
  - **Por qué:** la entrega incluye el repositorio, y las explicaciones son el material de estudio del grupo para el coloquio.
  - **Afecta:** `README.md`, `docs/`.
  - **Requisitos:**
    - [ ] README refleja el estado final: servicios, puertos (incluidos Grafana/Prometheus), cómo levantar y cómo probar.
    - [ ] Una `explicacion_*` por cada tema que se vaya a defender en el coloquio.
    - [ ] Esta planificación queda con todo marcado y las decisiones D-1 a D-6 resueltas.
  - **Terminada cuando:** alguien ajeno al grupo levanta y entiende el proyecto leyendo solo el README y `/docs`.

- [ ] **T-73 · Ensayo del coloquio** — transversal · depende de: T-71, T-72 · todos
  - **Qué:** guion de la demo, reparto de quién explica qué, y ensayo completo sobre un stack levantado de cero.
  - **Por qué:** la nota depende del coloquio, y hay partes (apertura del circuito, correlación de logs) que hay que saber mostrar en vivo.
  - **Afecta:** `docs/`.
  - **Requisitos:**
    - [ ] Guion con el orden de la demo y qué muestra cada integrante.
    - [ ] Cada requisito obligatorio del enunciado tiene un momento donde se muestra funcionando.
    - [ ] La demostración del Circuit Breaker está ensayada.
    - [ ] Ensayo completo hecho al menos una vez de punta a punta.
  - **Terminada cuando:** el grupo corre la demo entera sin improvisar y dentro del tiempo previsto.

---

## 7. Resumen de avance

| Fase | Tareas | Completadas |
|---|---|---|
| 0 — Cimientos | 3 | 1 |
| 1 — Núcleo de dominio | 11 | 0 |
| 2 — Integración y seguridad | 3 | 0 |
| 3 — Requisitos transversales | 2 | 0 |
| 4 — Frontend | 6 | 0 |
| 5 — Cierre y entrega | 4 | 0 |
| **Total** | **29** | **1** |

Actualizar esta tabla al cerrar cada tarea.
