# Plan de implementación — T-21 · Endpoint de ingesta de eventos

Tarea del Carril B, íntegramente en `match-ingestion-service`. Depende de T-20.

## 1. Contexto y Objetivos

`POST /api/matches/{matchId}/events` es la puerta de entrada de datos del sistema. Lo llama el proveedor externo y también el admin cuando simula eventos (T-65). Esta tarea lo implementa según el contrato congelado en T-01 (`docs/explicacion_contratos_api.md`, secciones 3 y 3.1): valida, persiste y devuelve el `eventId`. Publicar en Kafka es T-23, que se engancha en este mismo servicio después del `save`.

No se agrega seguridad: por D-2, `match-ingestion-service` no lleva Spring Security. El contrato dice que el endpoint es solo para admin, pero hoy nadie lo exige (R-7); se resuelve en T-40.

## 2. Requisitos a satisfacer

1. Valida el formato y rechaza los eventos incompletos con `400 INVALID_EVENT`, **sin persistir**.
2. Si el partido no existe en su propia base devuelve `404 MATCH_NOT_FOUND`. **No** valida el `teamId` contra el catálogo (D-1).
3. Persiste el evento y responde `201` con `{ "eventId": string }`.
4. Todos los errores usan el formato único `ErrorResponseDTO` (contrato, sección 1).

### Reglas de validación

| Campo | `GOAL` | `CARD` | `MATCH_END` |
|---|---|---|---|
| `type` | obligatorio, uno de los tres | ← | ← |
| `minute` | obligatorio, ≥ 0 | ← | ← |
| `teamId` | obligatorio, local o visitante del partido | ← | no aplica |
| `playerName` | obligatorio | obligatorio | no aplica |
| `cardType` | no aplica | obligatorio, `YELLOW` o `RED` | no aplica |
| `homeScore`, `awayScore` | obligatorios, ≥ 0 | no aplica | obligatorios, ≥ 0 |
| `description` | opcional | ← | ← |
| `occurredAt` | opcional, si falta se usa el momento de la ingesta | ← | ← |

- **"No aplica"** quiere decir que el campo se ignora y no se persiste. Así el documento en Mongo queda solo con los campos de su tipo, que es lo que T-20 quiere mostrar.
- Si el body trae `eventId`, se ignora: lo genera Mongo.
- El `matchId` que manda es el del path. Si el body trae uno **distinto**, se responde `400`, para evitar que un copy-paste mal hecho cargue un evento en otro partido.
- Un JSON malformado o con tipos incorrectos (`"minute": "abc"`) devuelve `400 INVALID_EVENT` y no un `500`.
- Orden de las validaciones: primero el formato (sin tocar la base), después la existencia del partido (`404`), después que el `teamId` juegue ese partido (`400`), y recién ahí se persiste.

### Decisiones tomadas (no cubiertas explícitamente por el contrato)

1. **`teamId` no aplica en `MATCH_END`.** El fin de partido es de los dos equipos, así que se guarda con `teamId` en `null`. T-23 publica un mensaje por equipo con el `teamId` de cada uno, como pide la sección 5 del contrato.
2. **`occurredAt` es opcional.** Si no viene, se usa `Instant.now()`. El proveedor puede mandarlo, pero el formulario del admin (T-65) no necesita pedir una hora.
3. **El `teamId` tiene que ser uno de los dos equipos del partido.** Es una validación local contra la propia base, no contra el catálogo, así que respeta D-1. Rechaza, por ejemplo, un gol de River en Colón-Unión.
4. **Cada evento actualiza el partido:**
   - `GOAL`: actualiza el marcador y pone el partido en `LIVE`.
   - `CARD`: pone el partido en `LIVE`.
   - `MATCH_END`: guarda el marcador final y pone el partido en `FINISHED`.

   El motivo es que `GET /api/matches` (T-22) devuelve `status` y marcador, y el muro de eventos del mockup muestra el marcador desde ahí. Sin esto, el partido seguiría en "0-0" después de ingestar goles. Son unas diez líneas en el mismo servicio. Si no se aprueba, se propone como tarea nueva.

   Queda afuera, porque no está en el contrato: rechazar eventos sobre un partido `FINISHED`.

## 3. Componentes a crear

**DTOs (`dto/`):**

- **`MatchEventDTO.java`**: `record` idéntico al de la sección 3.1 del contrato. Se usa como request, como respuesta en T-22 y como payload de Kafka en T-23.
- **`EventCreatedDTO.java`**: `record EventCreatedDTO(String eventId)`.
- **`ErrorResponseDTO.java`**: copia del de fan-identity (contrato, sección 1). Cada servicio tiene la suya; no se arma una librería compartida.

**Servicio (`service/MatchEventService.java`):**

- Inyecta por constructor `MatchRepository` y `MatchEventRepository`.
- `String ingestEvent(String matchId, MatchEventDTO request)`:
  1. Valida con `if` explícitos, igual que `NotificationService.createNotification` en fan-identity.
  2. Convierte `type` y `cardType` con `EventType.valueOf` / `CardType.valueOf` dentro de un `try/catch (IllegalArgumentException)`. El error dice cuáles son los valores válidos.
  3. Busca el partido, o lanza `MatchNotFoundException`.
  4. Arma el `MatchEvent` con un `switch` sobre el tipo que copia solo los campos que aplican.
  5. Hace `save`, actualiza el partido (decisión 4) y devuelve el id.
- Excepciones anidadas, como en fan-identity: `InvalidEventException` y `MatchNotFoundException`. T-22 reutiliza esta última.

**Controlador (`controller/MatchController.java`):**

- `@RequestMapping("/api/matches")`.
- `@PostMapping("/{matchId}/events")` devuelve `201` con `EventCreatedDTO`.
- T-22 le agrega los `GET` a este mismo controlador.

**Manejo de errores (`exception/GlobalExceptionHandler.java`):** un `@RestControllerAdvice` como el de fan-identity.

| Excepción | Respuesta |
|---|---|
| `InvalidEventException` | `400 INVALID_EVENT` |
| `MatchNotFoundException` | `404 MATCH_NOT_FOUND` |
| `HttpMessageNotReadableException` (JSON malformado o tipos incorrectos) | `400 INVALID_EVENT` |
| `Exception` (red de contención final) | `500 INTERNAL_ERROR`, con `log.error` |

No se tocan el gateway (`/api/matches/**` ya rutea), ni el compose, ni el `pom`.

## 4. Plan de verificación

Todo va por el gateway (`localhost:8080`). Mientras T-40 no esté hecha no hace falta token. Después de T-40, hay que agregar `-H "Authorization: Bearer <token del admin>"`.

1. `docker compose up -d --build match-ingestion-service cloud-gateway`.
2. GOAL válido. Tiene que responder `201` con `{"eventId":"..."}`:
   ```
   curl -i -X POST http://localhost:8080/api/matches/match-001/events -H "Content-Type: application/json" -d '{"teamId":"team-colon","type":"GOAL","minute":78,"description":"Gol de Colón","playerName":"Facundo Farías","homeScore":1,"awayScore":0}'
   ```
3. En `mongosh match_ingestion_db`:
   - `db.match_events.find({matchId:"match-001"})` muestra el evento con `occurredAt` cargado y sin `cardType`.
   - `db.matches.findOne({_id:"match-001"})` muestra 1-0 en `LIVE` (si se aprobó la decisión 4).
4. Un `CARD` válido responde `201`, y el documento en Mongo no tiene marcador.
5. Casos inválidos. Cada uno tiene que devolver `400 INVALID_EVENT`, y `db.match_events.countDocuments()` no cambia:
   - Sin `type`.
   - `"type":"PENALTY"`.
   - `GOAL` sin `playerName`.
   - `CARD` con `"cardType":"GREEN"`.
   - `"minute":-5`.
   - `"minute":"abc"`.
   - `"teamId":"team-river"`, que no juega `match-001`.
   - Body con `"matchId":"match-002"` enviado a `/match-001/events`.
6. `POST /api/matches/match-999/events` con un body válido devuelve `404 MATCH_NOT_FOUND`.
7. `MATCH_END` sin `teamId` sobre `match-001` responde `201`, y el partido queda `FINISHED` con el marcador final. Para devolver `match-001` a su estado inicial: `db.matches.deleteOne({_id:"match-001"})` y reiniciar el servicio (la semilla lo recrea).
8. Todas las respuestas de error tienen el shape `timestamp`, `status`, `error`, `message`, `path`.
