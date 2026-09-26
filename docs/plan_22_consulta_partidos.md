# Plan de implementación — T-22 · Consulta de partidos y eventos

Tarea del Carril B, íntegramente en `match-ingestion-service`. Depende de T-20.

## 1. Contexto y Objetivos

Son dos endpoints de lectura para el muro de eventos del panel principal (`docs/assets/mockup_panel_principal.png`): las tarjetas de partido con su marcador y el último evento de cada uno. Cumplen el contrato de T-01 (`docs/explicacion_contratos_api.md`, sección 3):

- `GET /api/matches?teamId=`
- `GET /api/matches/{matchId}/events`

Las respuestas no traen nombres de equipo, porque por D-1 `match-ingestion-service` no tiene catálogo. El frontend cruza `homeTeamId`/`awayTeamId` contra `/api/catalog/teams` (T-15).

Se asume que T-21 ya está hecha: `MatchController`, `MatchEventService`, `ErrorResponseDTO` y `GlobalExceptionHandler` ya existen. **Si T-22 se implementa antes**, se crean acá solo con lo que esta tarea necesita (el `MatchNotFoundException` y su handler) y T-21 agrega el resto.

## 2. Requisitos a satisfacer

1. **`GET /api/matches?teamId=`**: `teamId` es opcional. Si viene, devuelve los partidos donde ese equipo es local **o** visitante. Un `teamId` vacío se trata igual que si no viniera.
   Respuesta `200`: `[{ id, homeTeamId, awayTeamId, status, scheduledAt, homeScore, awayScore }]`.
2. **`GET /api/matches/{matchId}/events`**: devuelve `200` con `[MatchEventDTO]` en orden cronológico, o `404 MATCH_NOT_FOUND` si el partido no existe. Un partido que existe y no tiene eventos devuelve `[]`, no `404`.
3. Las respuestas incluyen el `teamId` de cada equipo participante.

**Orden** (el contrato no lo fija):

- Los partidos, por `scheduledAt` ascendente (orden de calendario, estable entre llamadas).
- Los eventos, por `occurredAt` ascendente. Se usa `occurredAt` y no `minute` porque es el momento real. En los eventos simulados por el admin coincide con el orden de ingesta (T-21 lo completa con `now()` si no viene).

## 3. Componentes a crear o modificar

- **`dto/MatchDTO.java`**: `record` con los campos del contrato. `status` va como `String`, igual que `type` en `MatchEventDTO`.
- **`repository/MatchRepository.java`**, se le agregan dos métodos:
  - `List<Match> findAllByOrderByScheduledAtAsc()`.
  - `List<Match> findByHomeTeamIdOrAwayTeamIdOrderByScheduledAtAsc(String homeTeamId, String awayTeamId)`: es una derived query, Spring Data arma el `$or`. Se llama pasando el mismo `teamId` dos veces.
- **`repository/MatchEventRepository.java`**, se le agrega `List<MatchEvent> findAllByMatchIdOrderByOccurredAtAsc(String matchId)`.
- **`service/MatchService.java`** (nuevo): `getMatches(String teamId)`. Con un `if` elige entre el listado completo y el filtrado, y mapea a `MatchDTO`.
- **`service/MatchEventService.java`** (de T-21), se le agrega `getEvents(String matchId)`:
  - Si `matchRepository.existsById(matchId)` da falso, lanza `MatchNotFoundException`. Es la misma excepción y el mismo handler `404` de T-21. Sin este chequeo, "partido inexistente" y "partido sin eventos" devolverían los dos `[]`.
  - Mapea a `MatchEventDTO` (`eventId` = `id` del documento) con un método privado de mapeo. T-23 reutiliza ese método para armar el payload de Kafka.
- **`controller/MatchController.java`**: se le agrega `MatchService` al constructor y dos métodos:
  - `@GetMapping` con `@RequestParam(required = false) String teamId`.
  - `@GetMapping("/{matchId}/events")`.

No se tocan el gateway, ni el compose, ni el `pom`.

## 4. Plan de verificación

Todo va por el gateway (`localhost:8080`). Después de T-40, hay que agregar el header `Authorization`.

1. `docker compose up -d --build match-ingestion-service cloud-gateway`.
2. `GET /api/matches` devuelve los 3 partidos semilla ordenados por fecha: `match-003`, `match-001`, `match-002`. Cada uno trae `homeTeamId`, `awayTeamId`, `status` y marcador (`null` en `match-002`).
3. Filtro por equipo:
   - `GET /api/matches?teamId=team-colon` devuelve solo `match-001`.
   - `?teamId=team-boca` devuelve `match-002`. Boca juega de visitante, así que esto confirma que el `$or` funciona.
   - `?teamId=team-racing` devuelve `[]` con `200`.
4. `GET /api/matches/match-003/events` devuelve los 3 eventos semilla en orden: `GOAL` (34'), `CARD` (60'), `MATCH_END` (90'). El `CARD` trae marcador `null` y el `MATCH_END` trae `teamId` `null`.
5. `GET /api/matches/match-002/events` devuelve `[]` con `200`: el partido existe pero no tiene eventos.
6. `GET /api/matches/match-999/events` devuelve `404 MATCH_NOT_FOUND` con el formato único de error.
7. Si T-21 ya está hecha: ingestar dos eventos en `match-001` y confirmar que aparecen en orden de ingesta, y que `GET /api/matches` refleja el marcador actualizado.
