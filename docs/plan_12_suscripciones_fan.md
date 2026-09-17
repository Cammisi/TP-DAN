# Plan de implementación — T-12 · Suscripciones del fan

Tarea del Carril A asignada a `fan-identity-service`.

## 1. Contexto y Objetivos

Implementar los endpoints que permiten a un usuario con rol Fan gestionar sus suscripciones a equipos deportivos:
- Seguir un equipo (`POST /api/fans/subscriptions`)
- Dejar de seguir un equipo (`DELETE /api/fans/subscriptions/{teamId}`)
- Listar los equipos seguidos (`GET /api/fans/subscriptions`)

Cumple con el contrato de APIs congelado en T-01 (`docs/explicacion_contratos_api.md`, sección 2.2).

## 2. Requisitos a satisfacer

1. **Autenticación:** Las operaciones se ejecutan exclusivamente sobre el `fanId` extraído del JWT en el SecurityContext (`@AuthenticationPrincipal`). Ningún identificador de usuario viaja en el body ni path.
2. **Idempotencia en suscripción:** Si el fan ya sigue al equipo, responder `200 OK` con los datos del equipo y no duplicar registros en base de datos. Si es una nueva suscripción, responder `201 Created`.
3. **Validación de catálogo:** Si el `teamId` no existe en la base de datos, rechazar con `404 Not Found` y código `TEAM_NOT_FOUND` en `ErrorResponseDTO`.
4. **Idempotencia en desuscripción:** Si se deja de seguir un equipo al cual no se estaba suscripto, responder `204 No Content` de manera transparente.
5. **Join sin desnormalización (Decisión D-1):** El listado resuelve el nombre del equipo y el nombre de la liga mediante un join relacional en PostgreSQL (`JOIN FETCH`), garantizando el modelo relacional elegido.

## 3. Componentes a modificar o crear

- **DTOs:**
  - `SubscribeRequestDTO(String teamId)`
  - `SubscriptionResponseDTO(String teamId, String teamName, String leagueName)`
  - `SubscriptionItemDTO(String teamId, String teamName, String leagueId, String leagueName)`
- **Excepciones:**
  - `TeamNotFoundException` manejada en `GlobalExceptionHandler`.
- **Persistencia:**
  - `SubscriptionRepository`: métodos de búsqueda por `fanId` y `teamId`, eliminación y consulta optimizada con join fetch.
- **Lógica de negocio:**
  - `SubscriptionService`: coordinación transaccional (`@Transactional`).
- **Controlador REST:**
  - `SubscriptionController`: mapeo de rutas bajo `/api/fans/subscriptions`.

## 4. Plan de verificación

Se ejecutan peticiones vía `cloud-gateway` (`http://localhost:8080`):
1. Login como fan y obtención de JWT.
2. Seguir un equipo válido -> 201 Created.
3. Seguir nuevamente el mismo equipo -> 200 OK (idempotente).
4. Seguir equipo inválido -> 404 TEAM_NOT_FOUND.
5. Listar suscripciones -> 200 OK con datos de liga y equipo.
6. Dejar de seguir -> 204 No Content.
7. Dejar de seguir de nuevo -> 204 No Content.
8. Verificación de persistencia en PostgreSQL (`SELECT * FROM subscriptions`).
