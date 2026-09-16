# Plan 01 — Congelar el contrato de APIs y el esquema del evento Kafka (T-01)

> Tarea: T-01 · Depende de: — (D-1 a D-6 ya resueltas) · Responsable: todos
> Este plan **es** el contenido propuesto para `docs/explicacion_contratos_api.md`. Se aprueba acá y después se vuelca al archivo final tal cual, sin sorpresas.

## 1. Objetivo

Dejar un contrato único y completo de todos los endpoints (públicos, autenticados e internos) y del evento de Kafka, para que los carriles A, B y C puedan implementar en paralelo sin preguntarse nada entre sí. No se toca código en esta tarea: es puramente documentación.

## 2. Alcance

Entra:
- Formato único de error para los 3 microservicios.
- Todos los endpoints listados en los requisitos de T-01 (auth, suscripciones, notificaciones, catálogo, partidos/eventos, ingesta).
- El endpoint de estado de `alert-dispatcher-service` (`/api/alerts/**`, D-6), porque ya está decidido y sin documentarlo T-31 no tiene contrato que implementar.
- Esquema completo del evento Kafka (tópico, clave de partición, payload, ejemplo).

No entra (fuera de alcance de T-01):
- Implementar nada de esto en código (eso es Fase 1).
- Definir el mecanismo del secreto compartido entre servicios para los endpoints internos (eso es T-41, acá solo se fija el path y que quedan fuera de `/api/**`).
- Definir el filtro de rol admin en el gateway (eso es T-40); acá se señala qué endpoints lo van a necesitar.

## 3. Documento a crear

`docs/explicacion_contratos_api.md`, con las secciones que siguen (4 a 9 de este plan), en el mismo orden.

## 4. Formato único de error

Los 3 microservicios devuelven el mismo shape ante cualquier error, como `record`:

```java
public record ErrorResponseDTO(
    Instant timestamp,
    int status,
    String error,      // código estable en UPPER_SNAKE_CASE, ej. "EMAIL_ALREADY_EXISTS"
    String message,     // mensaje legible, sin datos sensibles
    String path
) {}
```

Ejemplo:

```json
{
  "timestamp": "2026-09-16T21:45:00Z",
  "status": 404,
  "error": "TEAM_NOT_FOUND",
  "message": "El equipo 'xyz' no existe en el catálogo",
  "path": "/api/fans/subscriptions"
}
```

`error` es un código propio por condición (no el reason phrase de HTTP), para que el frontend pueda distinguir `EMAIL_ALREADY_EXISTS` de `INVALID_CREDENTIALS` sin parsear el mensaje.

## 5. Contratos — `fan-identity-service`

Todos bajo `/api/fans/**` salvo el catálogo (`/api/catalog/**`, propio prefijo por D-1) y los internos (`/internal/**`, fuera de lo que rutea el gateway).

### 5.1 Auth (público)

| Método | Path | Auth |
|---|---|---|
| POST | `/api/fans/auth/register` | público |
| POST | `/api/fans/auth/login` | público |

**POST /api/fans/auth/register**
- Request: `{ "email": string, "password": string }`
- 201: `{ "id": string, "email": string }`
- 409 `EMAIL_ALREADY_EXISTS` si el email ya existe.
- 400 `INVALID_REQUEST` si falta email/password o el formato es inválido.

**POST /api/fans/auth/login**
- Request: `{ "email": string, "password": string }`
- 200: `{ "token": string, "fanId": string, "role": "FAN" | "ADMIN" }`
- 401 `INVALID_CREDENTIALS` si el email no existe o la contraseña no matchea.

### 5.2 Suscripciones (autenticado, fan)

Todas operan siempre sobre el fan del JWT (nunca sobre un id del body/path).

| Método | Path | Auth |
|---|---|---|
| POST | `/api/fans/subscriptions` | fan |
| DELETE | `/api/fans/subscriptions/{teamId}` | fan |
| GET | `/api/fans/subscriptions` | fan |

**POST /api/fans/subscriptions** — seguir un equipo
- Request: `{ "teamId": string }`
- 201: `{ "teamId": string, "teamName": string, "leagueName": string }`
- 200 (idempotente) si ya estaba suscripto: mismo body, no duplica ni tira 500.
- 404 `TEAM_NOT_FOUND` si el `teamId` no existe en el catálogo.

**DELETE /api/fans/subscriptions/{teamId}** — dejar de seguir
- 204 si se borró.
- 200 o 204 también si no estaba suscripto (no es un error del cliente): no tiene sentido devolver 404 por algo que de por sí ya está en el estado deseado.

**GET /api/fans/subscriptions** — listar
- 200: `[{ "teamId": string, "teamName": string, "leagueId": string, "leagueName": string }]`
- Nombres resueltos por join en fan-identity (D-1), sin desnormalizar.

### 5.3 Notificaciones

| Método | Path | Auth |
|---|---|---|
| GET | `/api/fans/me/notifications` | fan |
| POST | `/internal/fans/notifications` | interno (D-3, se blinda en T-41) |

**GET /api/fans/me/notifications**
- 200: `[{ "id": string, "message": string, "matchId": string, "teamId": string, "eventId": string, "createdAt": ISO-8601 }]`
- Orden: de más nueva a más vieja. Solo las del fan del JWT.

**POST /internal/fans/notifications** — callback de alert-dispatcher (D-3)
- Request: `{ "fanId": string, "eventId": string, "matchId": string, "teamId": string, "message": string, "occurredAt": ISO-8601 }`
- 201: `{ "id": string }`
- 404 `FAN_NOT_FOUND` si el `fanId` no existe.
- Fuera de `/api/**`: el gateway no lo rutea, solo alcanzable dentro de la red Docker.

### 5.4 Catálogo (autenticado, por D-1)

| Método | Path | Auth |
|---|---|---|
| GET | `/api/catalog/leagues` | fan/admin |
| GET | `/api/catalog/teams` | fan/admin |

**GET /api/catalog/leagues**
- 200: `[{ "id": string, "name": string }]`

**GET /api/catalog/teams?name=&leagueId=**
- Ambos query params opcionales, se combinan con AND si están los dos.
- 200: `[{ "id": string, "name": string, "leagueId": string, "leagueName": string }]`
- El `id` es el mismo `teamId` que acepta `POST /api/fans/subscriptions` y que usa `match-ingestion-service` como string.

### 5.5 Suscriptores por equipo (interno, consume alert-dispatcher)

| Método | Path | Auth |
|---|---|---|
| GET | `/internal/fans/subscribers/{teamId}` | interno (se blinda en T-41) |

- 200: `[{ "fanId": string, "email": string }]` — coincide con el `FanSubscriberDTO` ya existente en `alert-dispatcher-service` (`fanId`, `email`), no hace falta tocarlo.
- Equipo sin suscriptos: `200` con lista vacía, nunca 404.
- Fuera de `/api/**`.

> **Reemplaza el path provisorio (R-5):** hoy `FanIdentityClient` apunta a `GET /api/fans/subscribers/{teamId}` (dentro de `/api/**`, alcanzable desde el host). El contrato definitivo lo mueve a `GET /internal/fans/subscribers/{teamId}`. Actualizar el `@FeignClient` queda para T-13, no se toca en esta tarea.

## 6. Contratos — `match-ingestion-service`

Todos bajo `/api/matches/**`.

| Método | Path | Auth |
|---|---|---|
| GET | `/api/matches` | fan/admin |
| GET | `/api/matches/{matchId}/events` | fan/admin |
| POST | `/api/matches/{matchId}/events` | admin |

**GET /api/matches?teamId=**
- `teamId` opcional, filtra partidos donde participe ese equipo.
- 200: `[{ "id": string, "homeTeamId": string, "awayTeamId": string, "status": "SCHEDULED"|"LIVE"|"FINISHED", "scheduledAt": ISO-8601, "homeScore": int|null, "awayScore": int|null }]`
- No incluye nombres de equipo (match-ingestion no tiene catálogo, D-1): el frontend cruza `homeTeamId`/`awayTeamId` contra `/api/catalog/teams`.

**GET /api/matches/{matchId}/events**
- 200: `[MatchEventDTO]` (ver 6.1), ordenados cronológicamente.
- 404 `MATCH_NOT_FOUND` si el partido no existe.

**POST /api/matches/{matchId}/events** — ingesta (también la usa el admin para simular)
- Request: `MatchEventDTO` sin `eventId` (lo genera el servicio).
- 201: `{ "eventId": string }`
- 400 `INVALID_EVENT` si falta un campo obligatorio o el `type` no es uno de los soportados. No persiste.
- 404 `MATCH_NOT_FOUND` si `matchId` no existe en su propia base. **No** valida `teamId` contra el catálogo de fan-identity (D-1: no vale la pena la llamada entre servicios).

### 6.1 `MatchEventDTO`

Un único shape para los tres tipos de evento (gol, tarjeta, fin de partido), con campos específicos en `null` cuando no aplican — así T-20 no necesita una jerarquía de clases para algo tan chico:

```java
public record MatchEventDTO(
    String eventId,       // null en el request de ingesta, lo asigna el servicio
    String matchId,
    String teamId,         // equipo al que pertenece el evento
    String type,           // "GOAL" | "CARD" | "MATCH_END"
    Integer minute,
    String description,
    String playerName,     // solo GOAL y CARD, null en MATCH_END
    String cardType,       // solo CARD: "YELLOW" | "RED", null en el resto
    Integer homeScore,     // solo GOAL y MATCH_END, null en CARD
    Integer awayScore,     // solo GOAL y MATCH_END, null en CARD
    Instant occurredAt
) {}
```

Ejemplo (gol):

```json
{
  "eventId": "evt-8f21",
  "matchId": "match-001",
  "teamId": "team-colon",
  "type": "GOAL",
  "minute": 78,
  "description": "Gol de Colón",
  "playerName": "Facundo Farías",
  "cardType": null,
  "homeScore": 1,
  "awayScore": 0,
  "occurredAt": "2026-09-16T21:45:00Z"
}
```

## 7. Contratos — `alert-dispatcher-service` (D-6)

Solo expone el estado del despachador para el panel de admin; el historial del fan se lee de `fan-identity-service` (5.3), no de acá.

| Método | Path | Auth |
|---|---|---|
| GET | `/api/alerts/status` | admin |

- 200: `{ "circuitState": "CLOSED"|"OPEN"|"HALF_OPEN", "lastProcessedEvents": [{ "eventId": string, "matchId": string, "teamId": string, "dispatchedAt": ISO-8601, "subscriberCount": int }] }`
- Contrato mínimo para que T-31 tenga algo concreto que implementar; puede crecer si esa tarea lo necesita, pero no se le agrega nada especulativo ahora.

## 8. Esquema del evento Kafka

- **Tópico:** `match-events`, declarado con un bean `NewTopic` en `match-ingestion-service` (no se depende de auto-creación).
- **Clave de partición:** `teamId`.
- **Payload:** el mismo `MatchEventDTO` de 6.1, ya con `eventId` asignado.
- **Decisión de diseño — `MATCH_END` involucra dos equipos:** como la clave es un único `teamId` y `alert-dispatcher-service` resuelve suscriptores por equipo, `match-ingestion-service` publica **un mensaje por equipo participante** cuando el evento es `MATCH_END` (mismo `eventId`, dos mensajes con `teamId` distinto). Para `GOAL` y `CARD` se publica un solo mensaje, con el `teamId` al que pertenece el evento. Así el consumer de alert-dispatcher no necesita casos especiales por tipo de evento.
- **Resiliencia de la publicación (T-23):** si Kafka está caído, el evento ya fue persistido en Mongo antes de intentar publicar; el fallo de publicación se loguea pero no revierte la persistencia ni falla el POST.

Ejemplo de mensaje (mismo evento de gol de 6.1, sin cambios: value = el DTO, key = `"team-colon"`).

## 9. Prefijo de endpoints internos

`/internal/**` en `fan-identity-service` agrupa los dos endpoints internos (5.3 y 5.5). El gateway solo declara rutas para `/api/fans/**`, `/api/catalog/**`, `/api/matches/**` y `/api/alerts/**` (ver `cloud-gateway/application.yaml`): al no existir una ruta `/internal/**`, esos endpoints quedan inalcanzables desde `localhost:8080` por construcción, sin necesidad de reglas negativas. Autenticarlos con un secreto compartido es alcance de T-41, no de esta tarea.

## 10. Riesgo detectado durante esta tarea (para anotar en la planificación)

**Quién valida que el llamante de `POST /api/matches/{matchId}/events` sea admin.** Por D-2, el gateway solo valida la firma del JWT y autenticación (no rol), y `match-ingestion-service` no lleva Spring Security propio porque no está expuesto al host. Con el diseño actual, un fan autenticado (no admin) podría llamar a este endpoint vía gateway. Dos salidas posibles, a decidir en T-40:
- El filtro del gateway (T-40) chequea el claim `role` para esta ruta puntual, además de la firma.
- O se acepta el riesgo porque no es explotable fuera del entorno académico y se documenta como limitación conocida.

Esto se anota como **R-7** en la sección 3 de `docs/planificacion_global.md` al aprobar este plan, para que T-40 lo tenga en cuenta.

## 11. Alineación con código ya existente

- `FanIdentityClient.getSubscribers` (`alert-dispatcher-service`) hoy apunta a `/api/fans/subscribers/{teamId}`. Pasa a `/internal/fans/subscribers/{teamId}` — cambio de una línea, se hace en **T-13**, no en esta tarea.
- `FanSubscriberDTO(String fanId, String email)` ya coincide con el contrato de 5.5. No requiere cambios.
- El resto de los servicios (`fan-identity-service`, `match-ingestion-service`) todavía no tienen controladores: no hay nada que realinear ahí, el contrato es la primera versión.

## 12. Pasos de implementación (una vez aprobado este plan)

1. Crear `docs/explicacion_contratos_api.md` volcando las secciones 4 a 9 tal cual quedaron aprobadas.
2. Agregar en `docs/planificacion_global.md`:
   - Marcar los 9 checkboxes de requisitos de T-01.
   - Marcar T-01 como hecha.
   - Sumar **R-7** (sección 10 de este plan) a la sección 3 de riesgos.
   - Actualizar la tabla de avance de la sección 7 (Fase 0: 1 → 2 completadas; Total: 1 → 2).
3. Commit con ambos archivos (`docs/explicacion_contratos_api.md` y `docs/planificacion_global.md`).

## 13. Verificación

Al ser una tarea de documentación, "ejecutar" significa releer el contrato completo contra los requisitos de T-01 y contra cada tarea de Fase 1 que depende de él (T-11 a T-15, T-20 a T-23, T-30, T-31), confirmando que cada una tiene todo lo que necesita para implementar sin volver a esta tarea. No hay código para correr.
