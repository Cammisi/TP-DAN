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

## Ampliación 2026-10-07 — escribir un equipo nuevo, no solo elegir

Pedido explícito del integrante: poder tipear equipos que no están en el catálogo (ej. "PSG", "Atlético Tucumán"), no solo elegir entre los ya cargados.

- **Backend — `fan-identity-service`:** nuevo `POST /api/catalog/teams` (admin), `CatalogService.createTeam(name, leagueId)`: si ya existe un equipo con ese `name` exacto lo reutiliza (`200`), si no lo crea (`201`). Nuevo `TeamRepository.findByName`. Excepciones `CatalogService.InvalidRequestException` (`400 INVALID_REQUEST`) y `CatalogService.LeagueNotFoundException` (`404 LEAGUE_NOT_FOUND`), mapeadas en el `GlobalExceptionHandler` existente.
- **Gateway:** `JwtValidationFilter.isAdminOnly` ahora también exige `ADMIN` para cualquier no-`GET` bajo `/api/catalog/**` (antes solo cubría `/api/matches/**` y `/api/alerts/**`) — sin esto, cualquier fan podría crear equipos.
- **Frontend — `/admin`:** cada `<select>` de equipo (local/visitante) suma una opción "Otro (escribir nombre)"; al elegirla aparecen un input de nombre y un `<select>` de liga. Al enviar: si el lado es "Otro", primero `createCatalogTeam({name, leagueId})` para obtener el id numérico, después `createMatch` con los ids resueltos. Si los dos lados resuelven al mismo id (mismo nombre tipeado dos veces, o mismo equipo elegido dos veces), se rechaza antes de llamar a `createMatch`.
- **No implementado a propósito:** autocompletar/tipeo con sugerencias del catálogo mientras se escribe (es un `<select>` + opción "Otro", no un combobox) — alcanza para el pedido y evita una dependencia nueva de UI.
- **Pendiente:** verificar en Docker (no se reconstruyó la imagen todavía, a pedido explícito del integrante — se hace en un solo lote junto con el resto de los cambios de esta sesión).
