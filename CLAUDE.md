# ¿Quién Juega?

Plataforma de seguimiento deportivo en tiempo real basada en microservicios Spring Boot, con descubrimiento de servicios (Eureka) y gateway único de entrada.

Trabajo práctico integrador — *Desarrollo de aplicaciones en la Nube*, UTN Santa Fe, 2026.
Alumnos: Bautista Lazzarini, Bautista Luetich, Jose Cammisi.

## Problema y propuesta

Los fanáticos del deporte consumen información de múltiples fuentes (redes sociales, sitios de noticias), lo que dificulta el seguimiento personalizado de eventos en tiempo real. **¿Quién Juega?** centraliza eventos deportivos de distintos proveedores y permite a los usuarios configurar suscripciones personalizadas (equipos/ligas). El sistema procesa los eventos en tiempo real y, mediante mensajería asincrónica (Kafka), despacha alertas a los usuarios interesados según su perfil.

**Alcance:**
- Sí: registro/autenticación de usuarios, suscripción a equipos/ligas, despacho de notificaciones internas en la plataforma.
- No: comunicación con sistemas externos que no sean proveedores de información (no hay integraciones salientes tipo email/push a terceros).

**Roles:**
- **Fan** — se registra/inicia sesión, busca equipos/ligas en el catálogo, gestiona suscripciones (seguir/dejar de seguir), visualiza su historial de alertas.
- **Administrador** — monitorea el estado de los microservicios (Eureka dashboard en `:8761` y Kafka UI en `:8090` ya cubren parte de esto a nivel infraestructura), simula la carga de un nuevo evento deportivo para disparar el flujo de punta a punta.

## Responsabilidad de cada microservicio

- **fan-identity-service** — ciclo de vida del usuario, perfiles y persistencia de suscripciones (relación muchos-a-muchos fan↔equipo). Emite y valida JWT (Spring Security). PostgreSQL porque la relación de suscripciones necesita integridad referencial y transaccionalidad.
- **match-ingestion-service** — gateway de datos externos: ingesta resultados/eventos de partidos, valida el formato y los persiste para consulta histórica. MongoDB porque los eventos son heterogéneos (un "gol" en fútbol no tiene los mismos atributos que un "quiebre de servicio" en tenis) y un esquema de documentos flexible encaja mejor que uno relacional. Emite eventos a Kafka cada vez que detecta un evento relevante.
- **alert-dispatcher-service** — consume los eventos de Kafka; por cada uno (ej. "Gol de Colón") consulta a `fan-identity-service` quiénes están suscritos y genera el registro de notificación. No tiene base de datos propia (principio de *Single Source of Truth*: los datos de fans viven solo en fan-identity-service) — el registro de notificación se persiste vía un callback HTTP a un endpoint interno de fan-identity-service (ver "Decisiones de arquitectura"). Esa consulta está protegida con **Circuit Breaker** (Resilience4j vía `spring-cloud-starter-circuitbreaker-resilience4j`): si fan-identity-service cae o tiene latencia alta, el circuito se abre para que alert-dispatcher-service no colapse esperando respuestas, en vez de eso aplica el fallback (ver más abajo).

### Scaffolding ya preparado para la llamada fan-identity-service ← alert-dispatcher-service

En `alert-dispatcher-service` (paquete `com.tpnube.alert_dispatcher_service`):
- `client/FanIdentityClient.java` — cliente Feign (`@FeignClient(name = "fan-identity-service")`, resuelto vía Eureka + load balancer) contra `GET /api/fans/subscribers/{teamId}`. **El path es provisorio** — hay que alinearlo con el contrato real que exponga fan-identity-service.
- `client/dto/FanSubscriberDTO.java` — DTO placeholder (`fanId`, `email`).
- `service/FanIdentityLookupService.java` — envuelve la llamada Feign con `CircuitBreakerFactory.create("fanIdentityService").run(...)`. El fallback hoy devuelve lista vacía; queda un `TODO` para encolar el evento y reintentar en vez de descartarlo.
- `config/CircuitBreakerConfiguration.java` — customiza el circuito por defecto (ventana de 10 llamadas, 50% de fallas para abrir, 15s en abierto, timeout de 3s por llamada).
- Se agregó `spring-cloud-starter-openfeign` al `pom.xml` y `@EnableFeignClients` en `AlertDispatcherServiceApplication`.

Importante: el pom usa `spring-cloud-starter-circuitbreaker-resilience4j` (la abstracción **Spring Cloud CircuitBreaker**, API programática vía `CircuitBreakerFactory`), no `resilience4j-spring-boot3`. Por eso el wiring usa `circuitBreakerFactory.create(...).run(...)` en vez de la anotación `@CircuitBreaker` — mezclar ambos estilos sin la dependencia correcta no compila.

## Decisiones de arquitectura

- **Persistencia del historial de alertas:** alert-dispatcher-service no tiene DB propia, pero necesita que el Fan pueda ver su historial de notificaciones. Se resuelve con un **callback HTTP**: después de resolver los suscriptos vía `FanIdentityLookupService`, alert-dispatcher-service hace un `POST` a un endpoint interno de fan-identity-service (a definir, ej. `POST /api/fans/{fanId}/notifications`) que persiste la notificación junto con los datos del fan. Mantiene la Single Source of Truth real (los datos de fans, incluido su historial, viven solo en fan-identity-service) en vez de duplicar estado en alert-dispatcher-service.
- **Frontend:** `frontend-app` se construye con **Next.js** (Pages Router, según lo visto en la materia), consumiendo exclusivamente `cloud-gateway` en `:8080`. Se eligió sobre React+Vite plano porque hay un módulo dedicado de la materia y el ruteo basado en archivos simplifica separar las pantallas por rol (Fan/Admin).

## Mapa del proyecto

```
tp-nube-deportes/
├── backend-app/
│   ├── infrastructure/
│   │   ├── eureka-server/      # Service Discovery — puerto 8761
│   │   └── cloud-gateway/      # API Gateway (Spring Cloud Gateway MVC) — puerto 8080
│   └── microservices/
│       ├── fan-identity-service/       # Identidad de hinchas — puerto 8081 — PostgreSQL
│       ├── match-ingestion-service/    # Ingesta de partidos — puerto 8082 — MongoDB + Kafka
│       └── alert-dispatcher-service/   # Despacho de alertas — puerto 8083 — Kafka
├── frontend-app/                             # Cliente que consume exclusivamente el gateway (puerto 8080)
├── docker-compose.yml
└── CLAUDE.md
```

## Puertos asignados

| Servicio                   | Puerto host | Expuesto a host | Notas |
|-----------------------------|:-----------:|:---:|-------|
| cloud-gateway                | 8080 | Sí  | Único puerto que debe consumir `frontend-app` |
| eureka-server                | 8761 | Sí  | Dashboard de registro de servicios |
| fan-identity-service          | 8081 | No  | Solo accesible dentro de la red Docker / vía gateway |
| match-ingestion-service       | 8082 | No  | Solo accesible dentro de la red Docker / vía gateway |
| alert-dispatcher-service      | 8083 | No  | Solo accesible dentro de la red Docker / vía gateway |
| PostgreSQL (fan-identity)     | 5432 | Sí  | DB: `fan_identity_db` |
| MongoDB (match-ingestion)     | 27017 | Sí | DB: `match_ingestion_db` |
| Kafka                          | 9092 | Sí  | Broker en modo KRaft (sin ZooKeeper). Listener interno para los contenedores: `kafka:19092` |
| Kafka UI                       | 8090 | Sí  | Panel web de administración de tópicos/consumers (`provectuslabs/kafka-ui`) |

Todos los servicios corren en la red Docker `tp-nube-network` y se resuelven entre sí por nombre de contenedor (`eureka-server`, `postgres`, `mongo`, `kafka`, etc.).

## Comandos Docker

Desde la raíz del repositorio:

```bash
# Compilar todas las imágenes (Maven multi-stage) y levantar el stack completo
docker compose up --build

# Levantar en segundo plano
docker compose up --build -d

# Reconstruir un único servicio
docker compose up --build fan-identity-service

# Ver logs de un servicio
docker compose logs -f cloud-gateway

# Apagar y limpiar contenedores (conserva los volúmenes de datos)
docker compose down

# Apagar y borrar también los volúmenes (reset completo de datos)
docker compose down -v
```

El orden de arranque está gobernado por `depends_on` con `condition: service_healthy`: las bases de datos y Kafka deben pasar su healthcheck antes de que arranquen los microservicios correspondientes, y `eureka-server` debe estar saludable antes que cualquier cliente Eureka (los 3 microservicios y `cloud-gateway`). `cloud-gateway` además espera a que los 3 microservicios estén saludables antes de levantar.

## Puntos a verificar antes de la entrega

- **Kafka en modo KRaft:** el broker corre sin ZooKeeper (`apache/kafka:3.9.0`, un solo nodo cumpliendo roles `broker,controller`). Tiene dos listeners: `kafka:19092` para tráfico interno entre contenedores (el que deben usar `match-ingestion-service` y `alert-dispatcher-service` vía `SPRING_KAFKA_BOOTSTRAPSERVERS`) y `localhost:9092` para herramientas externas al host. Los tópicos no se crean automáticamente salvo que el broker tenga auto-creación habilitada (por defecto sí la tiene) o que el código los declare explícitamente con `NewTopic` beans.
- **Spring Cloud Gateway Server WebMVC:** el prefijo de propiedades usado en `cloud-gateway/application.yaml` (`spring.cloud.gateway.server.webmvc.routes`) corresponde al artifact `spring-cloud-starter-gateway-server-webmvc` de Spring Cloud 2025.1.2. Es un módulo muy reciente — conviene correr `mvn spring-boot:run` una vez y confirmar contra la documentación de esa versión exacta que el namespace de propiedades no cambió.
- **Healthchecks sin Actuator:** `eureka-server`, `fan-identity-service`, `match-ingestion-service` y `alert-dispatcher-service` no tienen `spring-boot-starter-actuator` en su `pom.xml`, así que sus healthchecks en `docker-compose.yml` son un chequeo TCP simple (`/dev/tcp` vía `bash`) contra el puerto de la app, no un `GET /actuator/health`. Si se agrega Actuator más adelante, conviene migrar los healthchecks a HTTP para que reflejen el estado real de la app (DB, Kafka, etc.) y no solo si el puerto está abierto.
- **Credenciales en claro:** las credenciales de Postgres están hardcodeadas en `docker-compose.yml` para simplificar el entorno local del TP (Kafka no requiere autenticación en este setup local sin seguridad SASL/SSL). No usar estos valores fuera de este contexto académico.
