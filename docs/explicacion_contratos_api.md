# Contrato de APIs y esquema del evento Kafka

Contrato congelado en T-01. Es la referencia única para implementar los tres microservicios y el frontend sin coordinarse endpoint por endpoint. Ningún carril agrega un endpoint que no esté acá sin pasar antes por `docs/planificacion_global.md`.

Todas las rutas `/api/**` pasan por `cloud-gateway` (`:8080`), que resuelve por Eureka. Las rutas `/internal/**` no están declaradas en el gateway y por lo tanto no son alcanzables desde el host.

## 1. Formato único de error

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

## 2. Contratos — `fan-identity-service`

Todos bajo `/api/fans/**` salvo el catálogo (`/api/catalog/**`, propio prefijo por D-1) y los internos (`/internal/**`).

### 2.1 Auth (público)

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

### 2.2 Suscripciones (autenticado, fan)

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
- 204 también si no estaba suscripto: no es un error del cliente, ya está en el estado deseado.

**GET /api/fans/subscriptions** — listar
- 200: `[{ "teamId": string, "teamName": string, "leagueId": string, "leagueName": string }]`
- Nombres resueltos por join en fan-identity (D-1), sin desnormalizar.

### 2.3 Notificaciones

| Método | Path | Auth |
|---|---|---|
| GET | `/api/fans/me/notifications` | fan |
| POST | `/internal/fans/notifications` | interno, header `X-Internal-Api-Key` (D-3, T-41) |

**GET /api/fans/me/notifications**
- 200: `[{ "id": string, "message": string, "matchId": string, "teamId": string, "eventId": string, "createdAt": ISO-8601 }]`
- Orden: de más nueva a más vieja. Solo las del fan del JWT.

**POST /internal/fans/notifications** — callback de alert-dispatcher (D-3)
- Request: `{ "fanId": string, "eventId": string, "matchId": string, "teamId": string, "message": string, "occurredAt": ISO-8601 }`
- 201: `{ "id": string }`
- 404 `FAN_NOT_FOUND` si el `fanId` no existe.
- Fuera de `/api/**`: el gateway no lo rutea, solo alcanzable dentro de la red Docker.

### 2.4 Catálogo (autenticado, por D-1)

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

### 2.5 Suscriptores por equipo (interno, consume alert-dispatcher)

| Método | Path | Auth |
|---|---|---|
| GET | `/internal/fans/subscribers/{teamId}` | interno, header `X-Internal-Api-Key` (T-41) |

- 200: `[{ "fanId": string, "email": string }]` — coincide con el `FanSubscriberDTO` ya existente en `alert-dispatcher-service` (`fanId`, `email`).
- Equipo sin suscriptos: `200` con lista vacía, nunca 404.
- Fuera de `/api/**`.

> **Reemplaza el path provisorio (R-5):** hoy `FanIdentityClient` apunta a `GET /api/fans/subscribers/{teamId}` (dentro de `/api/**`, alcanzable desde el host). El contrato definitivo lo mueve a `GET /internal/fans/subscribers/{teamId}`. El cambio del `@FeignClient` se hace en T-13.

## 3. Contratos — `match-ingestion-service`

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
- 200: `[MatchEventDTO]` (ver 3.1), ordenados cronológicamente.
- 404 `MATCH_NOT_FOUND` si el partido no existe.

**POST /api/matches/{matchId}/events** — ingesta (también la usa el admin para simular)
- Request: `MatchEventDTO` sin `eventId` (lo genera el servicio).
- 201: `{ "eventId": string }`
- 400 `INVALID_EVENT` si falta un campo obligatorio o el `type` no es uno de los soportados. No persiste.
- 404 `MATCH_NOT_FOUND` si `matchId` no existe en su propia base. **No** valida `teamId` contra el catálogo de fan-identity (D-1: no vale la pena la llamada entre servicios).

> **R-7 (resuelto en T-40):** `match-ingestion-service` no tiene Spring Security propio (D-2), así que el rol lo chequea el gateway: con un token de fan este POST devuelve `403 FORBIDDEN` sin llegar al microservicio (ver sección 7).

### 3.1 `MatchEventDTO`

Un único shape para los tres tipos de evento (gol, tarjeta, fin de partido), con campos específicos en `null` cuando no aplican:

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

## 4. Contratos — `alert-dispatcher-service` (D-6)

Solo expone el estado del despachador para el panel de admin; el historial del fan se lee de `fan-identity-service` (2.3), no de acá.

| Método | Path | Auth |
|---|---|---|
| GET | `/api/alerts/status` | admin |

- 200:
  ```json
  {
    "circuitState": "CLOSED",
    "lastProcessedEvents": [
      { "eventId": string, "matchId": string, "teamId": string, "dispatchedAt": ISO-8601, "subscriberCount": int }
    ],
    "undispatchedEvents": [
      { "eventId": string, "matchId": string, "teamId": string, "failedAt": ISO-8601, "reason": string }
    ]
  }
  ```
- `undispatchedEvents` (agregado en T-31): eventos que no se pudieron despachar porque falló la consulta de suscriptores (circuito abierto o fan-identity caído), o porque fallaron **todos** los callbacks de notificación de ese evento. Es el registro que reemplaza al fallback silencioso original.
- `subscriberCount` en `lastProcessedEvents` es la cantidad de fans **efectivamente notificados**, no la cantidad de suscriptos que tenía el equipo: si el callback falla para alguno mientras el evento sigue apareciendo acá (no todos fallaron), el número ya descuenta esos fallos.
- Ambas listas guardan como máximo los últimos 20 elementos, más nuevos primero, en memoria: se reinician si el servicio se reinicia (ver `docs/explicacion_circuit_breaker.md`).

## 5. Esquema del evento Kafka

- **Tópico:** `match-events`, declarado con un bean `NewTopic` en `match-ingestion-service` (no se depende de auto-creación).
- **Clave de partición:** `teamId`.
- **Payload:** el mismo `MatchEventDTO` de 3.1, ya con `eventId` asignado.
- **Decisión de diseño — `MATCH_END` involucra dos equipos:** como la clave es un único `teamId` y `alert-dispatcher-service` resuelve suscriptores por equipo, `match-ingestion-service` publica **un mensaje por equipo participante** cuando el evento es `MATCH_END` (mismo `eventId`, dos mensajes con `teamId` distinto). Para `GOAL` y `CARD` se publica un solo mensaje, con el `teamId` al que pertenece el evento. Así el consumer de alert-dispatcher no necesita casos especiales por tipo de evento.
- **Resiliencia de la publicación (T-23):** si Kafka está caído, el evento ya fue persistido en Mongo antes de intentar publicar; el fallo de publicación se loguea pero no revierte la persistencia ni falla el POST.

Ejemplo de mensaje (mismo evento de gol de 3.1): `value` = el DTO de arriba, `key` = `"team-colon"`.

## 6. Prefijo de endpoints internos

`/internal/**` en `fan-identity-service` agrupa los dos endpoints internos (2.3 y 2.5). El gateway solo declara rutas para `/api/fans/**`, `/api/catalog/**`, `/api/matches/**` y `/api/alerts/**` (ver `cloud-gateway/application.yaml`): al no existir una ruta `/internal/**`, esos endpoints quedan inalcanzables desde `localhost:8080` por construcción. Además, desde T-40 el gateway exige token para todo lo que no sea público, así que `localhost:8080/internal/**` responde `401`.

**Autenticación entre servicios (T-41):** toda llamada a `/internal/**` tiene que traer el header `X-Internal-Api-Key` con la clave compartida (`INTERNAL_API_KEY`, leída del `.env`). `alert-dispatcher-service` lo agrega a cada llamada Feign.

| Caso | Respuesta |
|---|---|
| Sin header o con una clave incorrecta | `401 UNAUTHORIZED` |
| Con un JWT de fan y sin la clave | `403 FORBIDDEN` |
| Con la clave correcta | la respuesta normal del endpoint (2.3, 2.5) |

## 7. Autenticación en el gateway (T-40)

`cloud-gateway` valida el JWT (firma y expiración) **antes** de rutear, con el mismo secreto con el que lo firma `fan-identity-service` (`JWT_SECRET`). El token se reenvía intacto al microservicio de destino.

| Ruta | Acceso |
|---|---|
| `OPTIONS` (preflight de CORS) | lo responde el filtro de CORS |
| `/api/fans/auth/**` | público |
| cualquier método que no sea `GET` sobre `/api/matches/**` (hoy, la ingesta de eventos) | solo `ADMIN` |
| `/api/alerts/**` | solo `ADMIN` |
| cualquier otra ruta (incluidas `/internal/**` y `/actuator/**` del gateway) | cualquier token válido |

Errores que responde el propio gateway, con el formato de la sección 1:

| Caso | Status | `error` | `message` |
|---|---|---|---|
| Falta el header `Authorization: Bearer ...` | 401 | `UNAUTHORIZED` | `Falta el token de autenticación` |
| Firma inválida, token malformado o expirado | 401 | `UNAUTHORIZED` | `Token inválido o expirado` |
| Token válido sin rol `ADMIN` en una ruta de admin | 403 | `FORBIDDEN` | `Se requiere rol administrador` |

**CORS:** se aceptan requests del origen configurado en `cors.allowed-origins` (por defecto `http://localhost:3000`), con los métodos `GET`, `POST`, `DELETE` y los headers `Authorization` y `Content-Type`. Los errores `401`/`403` también llevan los headers CORS, así que el frontend puede leerlos.
