# Guion del coloquio

Cubre los 8 requisitos obligatorios del enunciado (`docs/enunciado.md`) más la característica extra (Resilience4J), cada uno con un momento concreto de la demo. Repartido en los tres carriles de `docs/planificacion_global.md` sección 5 — es una propuesta de punto de partida, no una asignación fija; lo natural es que cada uno muestre lo que construyó, pero ajústenlo como les resulte más cómodo.

**Precondición de toda la demo:** `.env` creado (`cp .env.example .env`) y `docker compose up --build -d` corrido **con un par de minutos de margen** antes de arrancar — el primer evento justo después de un arranque en frío puede demorar por un efecto transitorio de Kafka/Eureka ya documentado (R-11 en `planificacion_global.md`). Si de casualidad pasa en vivo, no es un error: es el pie perfecto para explicar el Circuit Breaker antes de tiempo.

## Reparto propuesto

| Carril | Quién | Qué muestra |
|---|---|---|
| **A — Identidad** | _(a definir)_ | Login/registro, seguridad (JWT, gateway, endpoints internos), SQL (Postgres) |
| **B — Dominio deportivo y alertas** | _(a definir)_ | Mensajería (Kafka), NoSQL (Mongo), Circuit Breaker, logs centralizados |
| **C — Frontend y observabilidad** | _(a definir)_ | Frontend completo, Eureka/Gateway, Docker, métricas |

## Orden de la demo (≈12-15 min)

### 1. Arquitectura y Docker (2 min) — quien presente el carril C

- Mostrar `docker compose ps`: 14 contenedores, los 9 que declaran healthcheck en `healthy` y `kafka-ui`, `prometheus`, `grafana`, `loki` y `promtail` en `Up` (no declaran healthcheck). Requisito: **"Ejecutar las aplicaciones en contenedores Docker."**
- Un diagrama rápido (el de `README.md`, sección Arquitectura) explicando: 3 microservicios Spring Boot + 1 frontend Next.js, cada uno con su base. Requisito: **"Distribuir la aplicación en al menos 3 microservicios Spring Boot y un frontend."**
- Abrir Eureka (`http://localhost:8761`): los 4 clientes (`FAN-IDENTITY-SERVICE`, `MATCH-INGESTION-SERVICE`, `ALERT-DISPATCHER-SERVICE`, `CLOUD-GATEWAY`) registrados. Requisito: **"Ejecutar las aplicaciones detrás de Spring Cloud Gateway y Eureka."**

### 2. El fan: registro, login, catálogo, suscripción (3 min) — carril A

- Desde `http://localhost:3000`: `/register` con un email nuevo → `/login` → panel principal.
- `/subscriptions`: buscar "river" (o cualquier equipo), seguirlo, aparece en "Mis suscripciones".
- Mencionar de paso: **SQL** — Postgres guarda `Fan`/`Subscription`/`Team`/`League`, con FK real entre suscripción y equipo (parte del requisito de bases de datos).

### 3. Seguridad (2 min) — carril A

Terminal, con el stack ya arriba:

```bash
curl -i http://localhost:8080/api/fans/subscriptions                          # 401, sin token
curl -i http://localhost:8080/api/fans/subscriptions -H "Authorization: Bearer token-trucho"  # 401, firma inválida
# Con el token de un fan (no admin):
curl -i -X POST http://localhost:8080/api/matches/match-001/events -H "Authorization: Bearer $TOKEN_FAN" -d '{}'  # 403
curl -i http://localhost:8080/internal/fans/subscribers/1                     # 401 — ni con token llega: no hay ruta
```

Requisito: **"Implementar seguridad."** Un párrafo de más: JWT validado en dos lugares (gateway y fan-identity, por qué cada uno — `docs/explicacion_seguridad.md`), endpoints internos con clave compartida (`X-Internal-Api-Key`).

### 4. El admin ingesta un evento → mensajería → NoSQL → alerta (3 min) — carril B

- `/admin` (logueado como `admin@quienjuega.com`): elegir el partido y equipo que el fan de la demo sigue, tipo `GOAL`, completar y enviar.
- Abrir Kafka UI (`http://localhost:8090`), tópico `match-events`: los dos mensajes recién publicados, uno por equipo del partido (la key es el equipo cuyos fans se notifican). Requisito: **"Usar mensajería asincrónica."**
- Mencionar: el evento se persistió primero en **MongoDB** (`match-ingestion-service`) — requisito de base **NoSQL** — y *después* se publicó; si Kafka estuviera caído, el evento no se pierde (`docs/explicacion_mensajeria_kafka.md`).
- Volver al frontend, `/alerts` del fan: la alerta nueva apareció (botón "Actualizar" si hace falta).

### 5. Circuit Breaker (3 min) — carril B

Siguiendo `docs/explicacion_circuit_breaker.md` sección 5, versión corta:

```bash
docker compose stop fan-identity-service
# Ingestar 3 eventos GOAL del mismo equipo desde /admin o con curl (siguen dando 201: match-ingestion no depende de fan-identity)
# Cada evento son 2 consultas (una por equipo): con 3 eventos el circuito ya se abre
curl -s http://localhost:8080/api/alerts/status -H "Authorization: Bearer $TOKEN_ADMIN"   # circuitState: "OPEN", eventos en undispatchedEvents
docker compose start fan-identity-service
# esperar a que aparezca UP en Eureka + ~15s de waitDurationInOpenState
# ingestar 2 eventos más (3 llamadas de prueba en HALF_OPEN = 2 eventos)
curl -s http://localhost:8080/api/alerts/status -H "Authorization: Bearer $TOKEN_ADMIN"   # circuitState: "CLOSED" de nuevo
```

Esta es la característica extra: **Resilience4J**, con un fallback real (no descarta eventos en silencio) y expuesto en `GET /api/alerts/status`.

### 6. Observabilidad (2 min) — carril C

- Grafana (`http://localhost:3001`, `admin`/`admin`), dashboard **"TP-DAN — Métricas de Rendimiento"**: requests HTTP, memoria JVM, y el panel de Circuit Breaker mostrando el `OPEN` que se acaba de provocar. Requisito: **"Implementar telemetría de métricas de rendimiento."**
- Grafana → Explore → datasource Loki, query `{service=~"match-ingestion-service|alert-dispatcher-service|fan-identity-service"} |= "<eventId del paso 4>"`: las tres líneas, una por servicio, en orden. Requisito: **"Implementar un almacén centralizado de logs."**

## Contingencia si algo no responde en vivo

`docs/quien-juega.http` (VS Code + extensión REST Client) reproduce el recorrido completo request por request, sin depender del frontend. Es el plan B: si una pantalla no carga o hay que mostrar un paso puntual rápido, correrlo desde ahí.

## Registro del ensayo

_(Completar acá después de correrlo entre los tres: fecha, tiempo total real, qué salió distinto a lo escrito, ajustes hechos al guion.)_

| Fecha | Tiempo total | Notas |
|---|---|---|
| | | |
