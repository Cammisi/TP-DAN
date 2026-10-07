# Plan T-77 · IDs numéricos para equipos

## Qué cambia

`Team.id` deja de ser un slug manual (`"team-colon"`) y pasa a `Long` autogenerado (`@GeneratedValue(IDENTITY)`), igual que `Fan`, `Subscription` y `Notification`. El contrato HTTP/Kafka/Mongo **no cambia de tipo**: `teamId` sigue siendo `String` en todos los DTOs y en los mensajes de Kafka (D-1) — solo que ahora el valor es un dígito (`"4"`) en vez de un slug.

## fan-identity-service

- **`Team.java`**: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`. Comentario actualizado: el `teamId` que viaja en Kafka/Mongo es `String.valueOf(este id)`, ya no el id en sí.
- **`TeamRepository`**: `JpaRepository<Team, String>` → `JpaRepository<Team, Long>`.
- **`CatalogService.searchTeams`**: `team.getId()` ahora es `Long` → `TeamCatalogDTO` sigue exponiendo `String id`, se mapea con `String.valueOf(team.getId())`.
- **`SubscriptionService`**:
  - Nuevo helper privado `parseTeamId(String teamId)`: `Long.parseLong`, y si falla (`NumberFormatException`) lanza `TeamNotFoundException(teamId)` — un teamId no numérico es "no existe", no un 500.
  - `subscribe`/`unsubscribe` parsean el `teamId` recibido antes de llamar al repositorio.
  - `getSubscribers(String teamId)` (la usa el endpoint interno que consulta alert-dispatcher): si no es numérico, devuelve lista vacía en vez de lanzar — un evento con un `teamId` que no es del catálogo simplemente no tiene suscriptos, no debe romper el pipeline de alertas.
  - Los métodos que devuelven `team.getId()` en un DTO (`SubscriptionResponseDTO`, `SubscriptionItemDTO`) usan `String.valueOf(...)`.
- **`SubscriptionRepository`**: los métodos con `teamId` como parámetro de comparación contra `team.id` (`findByFanIdAndTeamId`, `existsByFanIdAndTeamId`, `deleteByFanIdAndTeamId`, `findAllByTeamIdWithFan`) cambian su parámetro de `String` a `Long` — la ruta HTTP/JSON sigue siendo `String`, el `Long` vive solo dentro del service.
- **`DataSeeder`**: `seedTeams()` deja de pasar un id manual; se siembra en este orden fijo (determina los ids 1..7 en una base nueva): Colón, Unión, River, Boca, Racing, Liverpool, Manchester City.

## match-ingestion-service

- Su `DataSeeder` referencia los mismos equipos por id en los partidos semilla (`match-001` Colón/Unión, `match-002` River/Boca, `match-003` Liverpool/Manchester City) y en los eventos semilla de `match-003`. Se actualizan los literales a los ids numéricos que resultan del orden de siembra de arriba (`"1"`.."7"`). match-ingestion no valida contra el catálogo (D-1): esto es solo para que la demo (suscripción → alerta) siga funcionando con datos consistentes entre los dos servicios.
- Los tests (`MatchEventServiceTest`, y los de `alert-dispatcher-service`) **no se tocan**: tratan el `teamId` como string opaco, no necesitan corresponder a un equipo real del catálogo.

## Documentación

- `docs/quien-juega.http`: actualizar los `teamId` de ejemplo a los nuevos valores numéricos.
- `explicacion_contratos_api.md` y `explicacion_modelo_mongo.md`: actualizar los ejemplos que muestran `"team-colon"` como valor de `teamId`.

## Importante: requiere volumen de Postgres limpio

`ddl-auto: update` no altera el tipo de una columna existente (`varchar` → `bigint`), y `subscriptions.team_id` tiene esa misma columna como FK. Sin recrear el volumen, el arranque puede fallar a validar el schema o quedar con datos inconsistentes. Verificación en Docker: `docker compose down -v` (se pierde todo, incluido Mongo/Kafka — aceptable en desarrollo) o, más puntual, borrar solo el volumen `postgres_data`.

## Verificación

1. `mvn test` en fan-identity-service (`SubscriptionServiceTest` actualizado: ids numéricos, un caso con `teamId` no numérico).
2. Con Postgres limpio y el stack levantado: `GET /api/catalog/teams` devuelve ids `"1"`.."7"`; un fan se suscribe con ese id; un evento en match-ingestion con `teamId: "1"` genera una alerta para ese fan.
3. Confirmar que un `teamId` inválido en `POST /api/fans/subscriptions` (ej. `"abc"`) da `404 TEAM_NOT_FOUND`, no `500`.
