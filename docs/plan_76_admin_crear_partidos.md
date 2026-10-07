# Plan T-76 · Admin: crear partidos

## Qué se agrega

Un `POST /api/matches` en `match-ingestion-service`, solo para admin (el gateway ya exige `ADMIN` en todo lo que no sea `GET` bajo `/api/matches/**`), y un formulario en `/admin` que lo usa.

## Backend — `match-ingestion-service`

- **DTO nuevo** `dto/CreateMatchRequestDTO.java`: `record CreateMatchRequestDTO(String homeTeamId, String awayTeamId, Instant scheduledAt)`.
- **`MatchService.createMatch(CreateMatchRequestDTO)`**:
  - Valida `homeTeamId`/`awayTeamId` no vacíos y distintos entre sí (mismo equipo no juega contra sí mismo).
  - `scheduledAt` opcional: si viene null, `Instant.now()`.
  - Crea `Match` con `id = null` (Mongo le asigna un `ObjectId`, igual que ya hace `MatchEvent` para eventos no-semilla), `status = SCHEDULED`, `homeScore`/`awayScore` null.
  - Extraigo el mapeo `Match → MatchDTO` a un método privado reusado por `getMatches` y `createMatch`.
- **`MatchController`**: `@PostMapping` sobre `/api/matches` (sin path adicional), devuelve `201` con el `MatchDTO` creado.
- **Excepción**: `MatchService.InvalidMatchException` (mismo patrón que `InvalidEventException`), mapeada en `GlobalExceptionHandler` a `400` / `INVALID_MATCH`.
- **No se valida** `homeTeamId`/`awayTeamId` contra el catálogo de fan-identity — coherente con D-1 (match-ingestion no tiene catálogo propio). La UI de admin ya restringe la elección a equipos del catálogo.

## Frontend — `/admin`

- `lib/api.js`: `createMatch({ homeTeamId, awayTeamId, scheduledAt })` → `POST /api/matches`.
- `pages/admin.js`: nueva sección "Crear partido" arriba del buscador de T-75, con dos `<select>` de equipos (misma fuente `getCatalogTeams()` ya cargada) y un input de fecha/hora opcional.
- Al crear con éxito: refrescar `matches` (refetch `getMatches()`) y dejar seleccionado el partido nuevo, sin recargar la página.
- Reusar estilos existentes de `Admin.module.css` (`.field`, `.fieldWide`, `.submit`) — no se agregan clases nuevas salvo que no alcancen.

## Contrato

Agregar a `explicacion_contratos_api.md`, sección 3 (`/api/matches/**`): fila `POST /api/matches | admin` con el detalle del body/response, junto a los endpoints ya documentados.

## Verificación

1. `mvn test` en match-ingestion-service (casos: crea con datos válidos; rechaza mismo equipo; rechaza `homeTeamId` vacío).
2. Con el stack levantado: `POST /api/matches` con un token admin, dos equipos del catálogo sin partido semilla (ej. Racing Club) → `201`, aparece en `GET /api/matches`.
3. En el navegador: crear "River vs Racing" desde `/admin`, confirmar que aparece en el buscador (T-75) y que "Equipo" ofrece River/Racing; ingestar un evento para Racing.
