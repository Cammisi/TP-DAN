# Instrucciones para la IA — ¿Quién Juega?

Este archivo le dice a la IA **cómo trabajar** sobre este proyecto. No describe el dominio: eso está en el [README.md](README.md) (problema, alcance, arquitectura, roles, tecnologías, puertos, cómo levantar el stack). Leé el README primero si necesitás contexto del negocio.

Es un **trabajo práctico académico** de tres estudiantes (UTN Santa Fe, *Desarrollo de aplicaciones en la Nube*). El objetivo es demostrar los conceptos de la materia con código que el grupo pueda explicar en un coloquio, no construir un sistema de producción.

## Metodología de trabajo

Los tres integrantes trabajan en paralelo, cada uno con su IA, sobre el mismo repositorio. Para que eso no termine en tres estilos distintos de código, el flujo es siempre el mismo:

1. **Se toma una tarea de [docs/planificacion_global.md](docs/planificacion_global.md).** Es el único lugar donde se decide qué se hace y en qué orden. La próxima tarea es la primera sin marcar (`- [ ] **T-`) cuyas dependencias ya estén cumplidas.
2. **La IA genera `docs/plan_XX_<slug>.md`** con el plan de implementación de esa tarea (el número coincide con el ID: T-11 → `plan_11_auth_jwt.md`). El plan detalla archivos a tocar, endpoints, modelo de datos y pasos de verificación.
3. **El integrante revisa y aprueba el plan.** Recién ahí la IA implementa.
4. **Al terminar**, se marcan los checkboxes de la tarea en la planificación global, se actualiza la tabla de avance y se commitea.

Reglas que sostienen este flujo:

- **No implementes una tarea sin su `plan_*` aprobado**, salvo que el integrante lo pida explícitamente. Para tareas chicas y ya especificadas al detalle, preguntá si conviene saltear el paso.
- **No inventes alcance.** Si algo hace falta y no está en la tarea, avisá y proponé agregarlo como tarea nueva a la planificación global en vez de hacerlo por las tuyas.
- **Una tarea no se marca como terminada** hasta cumplir su criterio de *Terminada cuando*, verificado de verdad (ejecutando, no asumiendo).
- **Si encontrás una inconsistencia o un bloqueo**, anotalo en la sección de riesgos de la planificación global.

## Criterios de código

El código lo tienen que poder leer y defender tres estudiantes en un coloquio. Priorizá eso sobre cualquier otra consideración.

- **Simple antes que genérico.** Sin capas de abstracción, interfaces de un solo implementador, ni patrones que el TP no pida. Si una clase existe "por si mañana…", no va.
- **Directo antes que clever.** Un `if` explícito es mejor que un stream anidado de tres niveles. El criterio es: ¿lo entiende un compañero en la primera lectura?
- **Solo lo que la tarea pide.** Nada de features, endpoints, configuraciones ni optimizaciones que nadie pidió.
- **Manejo de errores explícito y honesto.** Errores con mensaje claro y código HTTP correcto; nada de tragarse excepciones en silencio ni dejar `TODO` sin avisar.
- **Comentarios en español, solo para el *por qué*, y de una línea.** Si el código ya dice *qué* hace, el comentario sobra. Para una decisión no obvia (por qué este timeout, por qué este fallback), un comentario corto al lado de la línea alcanza — no un párrafo. Si la explicación no entra en una o dos líneas, o ya está en un gotcha de este archivo o en un `docs/explicacion_*`/`plan_*`, no la repitas en el código: dejá como mucho una referencia (`// ver CLAUDE.md`, `// ver explicacion_circuit_breaker.md`). Nunca expliques la misma decisión en dos archivos (ej. una clase y la que la usa, o el código y el `application.yaml`): un solo lugar. Antes de dar por terminada una tarea, releé los comentarios que agregaste y sacá los que no superen esta prueba.
- **Escribí código que se parezca al que ya está.** Antes de crear algo nuevo, mirá cómo está resuelto lo equivalente en otro servicio.

## Convenciones del proyecto

- **Nombre del producto:** la plataforma se llama **¿Quién Juega?**. **FanAlert** es el nombre de la sección donde el fan recibe sus alertas (el centro de alertas), no el del producto — los mockups del informe lo usan como branding general, pero no es así.
- **Paquetes:** `com.tpnube.<servicio_con_guiones_bajos>` (tal como los generó Spring Initializr: `com.tpnube.fan_identity_service`). No renombrarlos.
- **Idioma:** nombres de clases, métodos y variables en inglés; comentarios y documentación en español.
- **DTOs:** como `record` de Java (ver `FanSubscriberDTO`).
- **Inyección de dependencias:** por constructor, escrito explícitamente, sin `@Autowired` en campos (ver `FanIdentityLookupService`).
- **Configuración:** todo valor de entorno (URLs, puertos, credenciales, secretos) se lee de `application.yaml` con default para desarrollo local y se sobreescribe por variable de entorno en `docker-compose.yml`. Nunca hardcodeado en el código Java. Los **secretos** (`JWT_SECRET`, `INTERNAL_API_KEY`) no se escriben en el compose: el compose los toma de un `.env` que git ignora, con valores de ejemplo en `.env.example` (ver `docs/explicacion_docker_compose.md`).
- **Lombok:** disponible en `fan-identity-service` y `match-ingestion-service`. **No está en `alert-dispatcher-service` ni en los servicios de infraestructura** — ahí, o se escribe el boilerplate a mano, o se agrega la dependencia y se avisa.

## Decisiones de arquitectura vigentes

- **El catálogo de ligas y equipos vive en `fan-identity-service` (PostgreSQL).** `League` y `Team` conviven con `Fan` y `Subscription`, así la suscripción tiene FK real al equipo. `match-ingestion-service` guarda partidos y eventos en Mongo referenciando `teamId` como string, sin catálogo propio y sin validarlo contra otro servicio. El catálogo se sirve bajo su propia ruta `/api/catalog/**` en el gateway.
- **Historial de alertas vía callback HTTP.** `alert-dispatcher-service` no tiene base de datos propia. Después de resolver los suscriptos, hace un `POST` a un endpoint interno de `fan-identity-service` que persiste la notificación. Mantiene el Single Source of Truth real (los datos de fans, incluido su historial, viven solo en fan-identity-service) en vez de duplicar estado.
- **Endpoints internos fuera de `/api/**`.** Los endpoints que consumen otros microservicios (suscriptores por equipo, alta de notificación) van bajo un prefijo que el gateway no rutea, así quedan inalcanzables desde el host por construcción. Además exigen el header `X-Internal-Api-Key` con la clave compartida `INTERNAL_API_KEY` (T-41): sin ella, fan-identity responde `401` aunque la llamada venga de dentro de la red Docker.
- **El JWT se valida en el gateway y en `fan-identity-service`.** El gateway es la primera línea: rechaza lo no autenticado antes de que llegue a los microservicios (todo lo que no es `/api/fans/auth/**` requiere token). fan-identity valida además porque necesita saber *qué* fan hace la request. `match-ingestion-service` y `alert-dispatcher-service` **no** llevan Spring Security propio: no están expuestos al host y solo se alcanzan por el gateway, así que el gateway también chequea el rol `ADMIN` para sus rutas sensibles: toda escritura (no `GET`) sobre `/api/matches/**` y todo `/api/alerts/**` (R-7). Si se agrega un endpoint de admin en otro prefijo, hay que sumarlo en `JwtValidationFilter.isAdminOnly`. El CORS del frontend se configura **solo** en el gateway; los microservicios no agregan headers CORS propios (se duplicarían).
- **El rol Administrador es un `ROLE_ADMIN` real** en fan-identity, viaja en los claims del JWT y hay un usuario admin sembrado.
- **`/api/alerts/**` sirve solo el estado del despachador** (últimos eventos procesados, estado del circuito) para el panel de admin. El historial de alertas del fan **no** se lee de ahí, sino de `GET /api/fans/me/notifications`.
- **Observabilidad: Prometheus + Grafana + Loki + Promtail.** Métricas: Actuator + Micrometer → Prometheus → Grafana. Logs: SLF4J/Logback → Promtail → Loki → el mismo Grafana. Ojo con la confusión habitual: SLF4J escribe los logs pero no los centraliza, Prometheus guarda números y no texto, y Grafana no almacena nada. El "almacén centralizado de logs" que pide el enunciado es **Loki**.
- **Frontend con Next.js (Pages Router)**, consumiendo exclusivamente `cloud-gateway` en `:8080`. Se eligió sobre React+Vite plano porque hay un módulo dedicado de la materia y el ruteo por archivos simplifica separar las pantallas por rol.

Estas decisiones están registradas con su fundamento en la sección 2 de la [planificación global](docs/planificacion_global.md). No las contradigas sin avisar; si una tarea parece requerir lo contrario, planteálo antes de implementar.

## Gotchas técnicos

Cosas que ya costaron tiempo o que pueden romper de formas poco obvias:

- **Circuit Breaker: una sola API.** El `pom` usa `spring-cloud-starter-circuitbreaker-resilience4j` (abstracción **Spring Cloud CircuitBreaker**, API programática vía `CircuitBreakerFactory`), no `resilience4j-spring-boot3`. Por eso el wiring es `circuitBreakerFactory.create(...).run(...)` y **no** la anotación `@CircuitBreaker`: mezclar ambos estilos sin la dependencia correcta no compila.
- **Stack muy reciente.** Spring Boot 4.1 + Java 25 + Spring Cloud 2025.1.2. Varios starters cambiaron de nombre respecto de Boot 3 (`spring-boot-starter-web` → `spring-boot-starter-webmvc`, `spring-kafka` → `spring-boot-starter-kafka`, starters de test separados por módulo). Ante la duda, verificá contra el `pom` de un servicio que ya funcione, no contra la memoria de cómo era en Boot 3.
- **Spring Cloud Gateway Server WebMVC.** El prefijo `spring.cloud.gateway.server.webmvc.routes` en `cloud-gateway/application.yaml` corresponde al artifact `spring-cloud-starter-gateway-server-webmvc` de Spring Cloud 2025.1.2. Es un módulo nuevo: confirmá el namespace contra la documentación de esa versión exacta si las rutas no levantan.
- **Kafka en modo KRaft, dos listeners.** `kafka:19092` para tráfico entre contenedores (el que usan los microservicios vía `SPRING_KAFKA_BOOTSTRAPSERVERS`) y `localhost:9092` para herramientas del host. Los tópicos conviene declararlos explícitamente con beans `NewTopic` en vez de depender de la auto-creación del broker.
- **Healthchecks HTTP contra Actuator, en el gateway por otro puerto.** Desde T-50 los 5 servicios Spring tienen Actuator y su healthcheck en `docker-compose.yml` es `curl -f .../actuator/health` (por eso cada `Dockerfile` instala `curl`). En `cloud-gateway` Actuator vive en `management.server.port: 8084`, que no se publica al host: en el `8080` el `JwtValidationFilter` le respondería `401` al healthcheck y a Prometheus.
- **`fan-identity-service` autentica todo lo que no esté en `permitAll`.** Su `SecurityConfig` termina en `anyRequest().authenticated()`: un endpoint nuevo que tenga que ser público (como `/actuator/**`, que agregó T-50) hay que sumarlo explícitamente ahí, o responde `401`.
- **`eureka-server` necesita `@EnableEurekaServer` en la clase principal.** Sin ella, `spring-cloud-starter-netflix-eureka-server` no activa su auto-configuración de servidor y el arranque falla con `NoSuchBeanDefinitionException: TransportClientFactories` al intentar auto-registrarse como cliente. Se detectó y corrigió en T-02.
- **Healthcheck de `kafka` necesita `timeout: 12s`, no `5s`.** El script `kafka-broker-api-versions.sh` levanta su propia JVM cliente para consultar al broker y eso solo tarda ~7s, independientemente de la carga de la máquina. Con `5s` el contenedor queda `unhealthy` de forma permanente y bloquea a todo lo que dependa de él (`alert-dispatcher-service`, `match-ingestion-service`). Corregido durante T-13 (2026-09-17).
- **Healthcheck de `mongo` necesita `timeout: 10s`, no `5s` — mismo problema que el de `kafka`.** `mongosh --eval` levanta su propio proceso Node.js en cada chequeo (no es la query lo lento); medido a mano tardó entre 1.9 y 4.4 s según la carga de la máquina, así que con `5s` el contenedor queda `unhealthy` de forma intermitente incluso cuando Mongo funciona perfecto (confirmado consultando datos reales por la API mientras el healthcheck lo marcaba `unhealthy`). Detectado el 2026-09-30.
- **En Boot 4, la property de conexión a MongoDB es `spring.mongodb.uri`, no `spring.data.mongodb.uri`.** La vieja quedó deprecada a nivel `error` (verificado en el metadata de `spring-boot-mongodb-4.1.0.jar`) y ya no se bindea: el driver cae en su default (`localhost:27017`) sin avisar. La variable de entorno correspondiente en `docker-compose.yml` es `SPRING_MONGODB_URI`. Detectado y corregido en T-20 (R-8).
- **En Spring Kafka 4, usar `JacksonJsonSerializer`/`JacksonJsonDeserializer`, no `JsonSerializer`/`JsonDeserializer`.** Estos últimos están `@Deprecated` (usan Jackson 2; los nuevos usan Jackson 3, el mismo que trae Boot 4). Además, entre microservicios conviene `spring.json.add.type.headers: false` / `spring.json.use.type.headers: false`: el contrato entre servicios es el JSON (T-01), no el nombre de una clase Java, que ni siquiera existe igual en el consumer. En el consumer, envolver el deserializer con `ErrorHandlingDeserializer` — sin él, un mensaje que no deserializa bien bloquea la partición reintentando para siempre. Implementado en T-23/T-30.
- **Las properties de Feign van bajo `spring.cloud.openfeign.client.*`, no `feign.client.*`.** El prefijo viejo (anterior a OpenFeign 4) se ignora sin avisar: así quedó sin aplicar el timeout de T-31 hasta T-41 (R-10). Lo que se pueda, configurarlo en Java (como el `RequestInterceptor` del header interno). Una clase de `@FeignClient(configuration = ...)` **no** lleva `@Configuration`: si la lleva, el component scan la levanta y sus beans pasan a aplicarse a todos los clientes Feign (ver `FanIdentityClientConfiguration`). Lo que vaya por YAML, cubrirlo con un test que lea `FeignClientProperties` (ver `AlertDispatcherServiceApplicationTests`). En general: si el IDE marca una property como desconocida, verificala contra el `@ConfigurationProperties` del jar antes de descartar el warning.
- **Para leer el estado de un Circuit Breaker de Spring Cloud CircuitBreaker (Resilience4J), inyectar `CircuitBreakerRegistry` y usar `registry.find(id)`, no `registry.circuitBreaker(id)`.** Este último lo crea con la config default si no existe, y ahí se pierde la config real que declaró `CircuitBreakerConfiguration` para ese id. El bean `CircuitBreakerRegistry` lo expone la autoconfiguración de `resilience4j-spring-boot3` (dependencia transitiva de `spring-cloud-starter-circuitbreaker-resilience4j`), no hay que agregar nada al `pom`. Implementado en T-31.
- **El server standalone de Next.js (`output: "standalone"`) necesita `ENV HOSTNAME=0.0.0.0` en el Dockerfile.** Sin eso, hereda el `HOSTNAME` que Docker le setea siempre (el id del contenedor) y escucha solo en la IP interna de la red Docker: el contenedor arranca, pero ni el healthcheck ni el puerto publicado al host lo alcanzan. Detectado y corregido en T-60.
- **`docker compose build` de una imagen pesada (ej. `next build` de `frontend-app`) puede tumbar el daemon de Docker Desktop si ya hay muchos contenedores corriendo** (los otros 13 del stack, en una máquina con pocos recursos). Se manifiesta como comandos `docker` que quedan colgados sin salida y después responden `500`/`context deadline exceeded`. Se soluciona reiniciando Docker Desktop (o `wsl --shutdown` si con reiniciar la app no alcanza); se evita construyendo la imagen **antes** de levantar el resto del stack, no con todo ya arriba. Detectado en T-60 (2026-09-30).

## Qué no hacer

- **No agregues dependencias** al `pom.xml` o al `package.json` sin justificar por qué hacen falta y avisarlo.
- **No inventes endpoints** fuera del contrato definido en `docs/explicacion_contratos_api.md` (tarea T-01). Si falta uno, proponelo, no lo agregues por tu cuenta.
- **No toques `docker-compose.yml`** sin actualizar también `docs/explicacion_docker_compose.md`.
- **No metas secretos en el código ni en el repositorio.** Un secreto nuevo va al `.env` (y un valor de ejemplo a `.env.example`), nunca al compose ni al código. Las credenciales de Postgres escritas en `docker-compose.yml` son una simplificación deliberada del entorno local, anterior al `.env`; no repliques ese patrón.
- **No loguees contraseñas, tokens ni datos personales.**
- **No marques una tarea como terminada sin verificarla ejecutando.** Si no la pudiste probar, decilo.
- **No reescribas código que funciona** para "mejorarlo" si la tarea no lo pide.
