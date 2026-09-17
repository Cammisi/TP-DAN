# Plan de implementación — T-15 · Endpoints de catálogo de ligas y equipos

Tarea del Carril A: `fan-identity-service` (endpoints de lectura) + `cloud-gateway` (ruta nueva).

## 1. Contexto y Objetivos

El Fan necesita buscar equipos o ligas en el catálogo **antes** de suscribirse (T-12 ya exige un `teamId` válido). Por D-1 el catálogo (`League`, `Team`) vive en `fan-identity-service`/PostgreSQL, así que esta tarea es puramente de lectura sobre entidades que ya existen desde T-10: no hay modelo nuevo, solo dos endpoints y su ruta en el gateway.

Cumple el contrato ya congelado en T-01 (`docs/explicacion_contratos_api.md`, sección 2.4):

- `GET /api/catalog/leagues`
- `GET /api/catalog/teams?name=&leagueId=`

Va bajo su propio prefijo `/api/catalog/**` (no `/api/fans/**`) porque conceptualmente no es un recurso del fan, es el catálogo deportivo — así lo decidió D-1.

## 2. Requisitos a satisfacer

1. **`GET /api/catalog/leagues`** → `200: [{ "id": string, "name": string }]`.
2. **`GET /api/catalog/teams?name=&leagueId=`** → ambos query params opcionales, se combinan con **AND** si están los dos. `200: [{ "id": string, "name": string, "leagueId": string, "leagueName": string }]`. El `id` es el mismo `teamId` que acepta `POST /api/fans/subscriptions` (T-12), sin transformación: es literalmente `Team.id`.
3. **Auth:** `fan/admin`, es decir, cualquier fan autenticado (no hace falta chequear rol). El gateway ya exige JWT válido (D-2) y `SecurityConfig` de `fan-identity-service` ya cubre `/api/catalog/**` con `anyRequest().authenticated()` — no hace falta tocar seguridad.
4. **Ruta del gateway:** `/api/catalog/**` enrutando a `FAN-IDENTITY-SERVICE` vía Eureka, mismo patrón que las rutas ya declaradas para los otros dos microservicios.

Sin casos de error nuevos: no hay 404 en el contrato para estos dos endpoints (una liga o búsqueda sin resultados devuelve lista vacía, no error).

## 3. Componentes a crear o modificar

**`fan-identity-service`:**

- **DTOs nuevos (`dto/`):**
  - `LeagueDTO(String id, String name)`.
  - `TeamCatalogDTO(String id, String name, String leagueId, String leagueName)` — nombre distinto de `SubscriptionItemDTO` (que tiene los mismos 4 campos pero significa otra cosa: una suscripción, no un item de catálogo) para no acoplar dos contratos distintos al mismo record.
- **`TeamRepository`:** nuevo método de búsqueda combinada, mismo estilo `@Query` con `JOIN FETCH` que ya usa `SubscriptionRepository`:
  ```java
  @Query("SELECT t FROM Team t JOIN FETCH t.league l " +
         "WHERE (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
         "AND (:leagueId IS NULL OR l.id = :leagueId) " +
         "ORDER BY t.name")
  List<Team> search(@Param("name") String name, @Param("leagueId") String leagueId);
  ```
  Los `:name IS NULL OR ...` cubren los 4 casos (sin filtros, solo nombre, solo liga, ambos) con una sola query.
- **`LeagueRepository`:** nuevo método `List<League> findAllByOrderByName()` (derived query), para que el listado de ligas salga en un orden estable — el contrato no lo exige pero evita un orden arbitrario de Postgres en la demo.
- **`CatalogService` (nuevo, en `service/`):**
  - `getLeagues()`: mapea `leagueRepository.findAllByOrderByName()` a `List<LeagueDTO>`.
  - `searchTeams(String name, String leagueId)`: delega en `teamRepository.search(...)` y mapea a `List<TeamCatalogDTO>` (`team.getId()`, `team.getName()`, `team.getLeague().getId()`, `team.getLeague().getName()`).
- **`CatalogController` (nuevo, en `controller/`):** `@RequestMapping("/api/catalog")`.
  - `@GetMapping("/leagues")` → `CatalogService.getLeagues()`.
  - `@GetMapping("/teams")` con `@RequestParam(required = false) String name` y `@RequestParam(required = false) String leagueId` → `CatalogService.searchTeams(name, leagueId)`.

**`cloud-gateway`:**

- **`application.yaml`:** agregar una cuarta ruta, mismo patrón que las existentes:
  ```yaml
  - id: fan-identity-catalog
    uri: lb://FAN-IDENTITY-SERVICE
    predicates:
      - Path=/api/catalog/**
  ```
  (Va como ruta separada de `fan-identity-service` porque el predicado `Path=/api/fans/**` no matchea `/api/catalog/**`; no hace falta tocarla.)

No hace falta tocar `SecurityConfig`, `docker-compose.yml` ni ningún DTO existente.

## 4. Plan de verificación

1. `docker compose up -d --build fan-identity-service cloud-gateway`.
2. Login con un fan semilla (T-11) para obtener el JWT.
3. `GET http://localhost:8080/api/catalog/leagues` con el JWT → `200` con las ligas semilla (`Liga Profesional de Fútbol`, `Premier League`).
4. `GET http://localhost:8080/api/catalog/teams` (sin filtros) con el JWT → `200` con los 7 equipos semilla, cada uno con `id`, `name`, `leagueId`, `leagueName`.
5. `GET http://localhost:8080/api/catalog/teams?name=uni` → solo `Unión` (que, como todo el mundo sabe, le gana a Colón).
6. `GET http://localhost:8080/api/catalog/teams?leagueId=premier-league` → solo los equipos de Premier League.
7. `GET http://localhost:8080/api/catalog/teams?name=river&leagueId=liga-profesional` → un resultado (`River Plate`); con `leagueId=premier-league` en su lugar → lista vacía (AND real, no OR).
8. Cierre del ciclo con T-12: tomar el `id` de un equipo devuelto por `/api/catalog/teams` y usarlo en `POST /api/fans/subscriptions` → `201`, confirmando que el `teamId` del catálogo es el mismo que acepta la suscripción.
9. Sin JWT (o con uno inválido) a cualquiera de los dos endpoints → `401` (ya lo garantiza `anyRequest().authenticated()`, se confirma igual).
