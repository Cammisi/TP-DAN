# Plan de implementación — T-13 · Endpoint de suscriptores por equipo

Tarea del Carril A asignada a `fan-identity-service` (y un ajuste puntual en `alert-dispatcher-service`).

## 1. Contexto y Objetivos

Exponer el endpoint interno que `alert-dispatcher-service` consume para resolver a qué fans notificar cuando llega un evento de un equipo:

- `GET /internal/fans/subscribers/{teamId}` en `fan-identity-service`.

Cumple con el contrato congelado en T-01 (`docs/explicacion_contratos_api.md`, sección 2.5) y resuelve R-5: hoy `FanIdentityClient` en `alert-dispatcher-service` apunta al path provisorio `GET /api/fans/subscribers/{teamId}` (dentro de `/api/**`, alcanzable desde el host). El contrato definitivo lo mueve a `/internal/fans/subscribers/{teamId}`, fuera del prefijo que rutea el gateway.

## 2. Requisitos a satisfacer

1. **Shape de respuesta:** `200` con `[{ "fanId": string, "email": string }]`, coincidiendo campo a campo con `FanSubscriberDTO` ya existente en `alert-dispatcher-service`.
2. **Equipo sin suscriptos:** `200` con lista vacía, nunca `404`.
3. **Fuera de `/api/**`:** el path va bajo `/internal/fans/**`. El gateway solo declara rutas para `/api/fans/**`, `/api/catalog/**`, `/api/matches/**` y `/api/alerts/**` (`cloud-gateway/application.yaml`), así que este endpoint queda inalcanzable desde `localhost:8080` por construcción, sin tocar el gateway.
4. **Sin autenticación de usuario:** `SecurityConfig` de `fan-identity-service` ya tiene `/internal/**` en `permitAll()` (con el comentario de que se blinda con secreto compartido en T-41). No hace falta tocar seguridad en esta tarea.
5. **Alinear el Feign client:** actualizar `FanIdentityClient.getSubscribers` en `alert-dispatcher-service` al nuevo path y borrar el comentario de "path provisorio".

## 3. Componentes a modificar o crear

**`fan-identity-service`:**

- **DTO nuevo:** `dto/SubscriberDTO.java` — `record SubscriberDTO(String fanId, String email)`. Va en `fan-identity-service` con su propio nombre (no puede importar el DTO de `alert-dispatcher-service`, son módulos Maven separados); el contrato exige que el JSON coincida campo a campo con `FanSubscriberDTO`.
- **`SubscriptionRepository`:** nuevo método `findAllByTeamIdWithFan(String teamId)` con `@Query("SELECT s FROM Subscription s JOIN FETCH s.fan WHERE s.team.id = :teamId")`, mismo patrón que `findAllByFanIdWithTeamAndLeague` ya existente.
- **`SubscriptionService`:** nuevo método `getSubscribers(String teamId)` que mapea el resultado del repositorio a `List<SubscriberDTO>` (`fan.getId().toString()`, `fan.getEmail()`).
- **Controlador nuevo:** `controller/InternalFanController.java`, `@RequestMapping("/internal/fans")`, con `@GetMapping("/subscribers/{teamId}")` que delega en `SubscriptionService.getSubscribers`. Se crea aparte de `SubscriptionController` porque vive bajo un prefijo y un contrato de auth distintos (interno vs. fan autenticado).

**`alert-dispatcher-service`:**

- **`FanIdentityClient`:** cambiar `@GetMapping("/api/fans/subscribers/{teamId}")` por `@GetMapping("/internal/fans/subscribers/{teamId}")` y quitar el comentario de path provisorio.

Nada de esto toca `cloud-gateway`, `SecurityConfig` ni `docker-compose.yml`.

## 4. Plan de verificación

1. `docker compose up -d --build fan-identity-service alert-dispatcher-service` (rebuild de los dos servicios tocados).
2. Desde dentro de la red Docker (por ejemplo `docker compose exec alert-dispatcher-service curl http://fan-identity-service:8081/internal/fans/subscribers/<teamId>`), confirmar `200` con la lista de fans suscriptos a un equipo con suscriptores reales (usar un `teamId` de la semilla al que se haya suscripto un fan de prueba vía T-12).
3. Mismo chequeo con un `teamId` existente sin suscriptos -> `200` con `[]`.
4. Confirmar que `GET http://localhost:8080/internal/fans/subscribers/<teamId>` (vía gateway, desde el host) devuelve `404` del propio gateway por no existir la ruta — evidencia de que quedó fuera de `/api/**`.
5. Reiniciar `alert-dispatcher-service` y comprobar en sus logs (o con un test manual del `FanIdentityLookupService`) que el Feign client ya resuelve contra el nuevo path sin el `TODO`/comentario de provisorio.
