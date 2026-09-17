# Plan de implementación — T-14 · Historial de notificaciones

Tarea del Carril A, íntegramente en `fan-identity-service`.

## 1. Contexto y Objetivos

`alert-dispatcher-service` no tiene base propia (D-3): cuando resuelve a qué fans notificar, persiste el historial vía un callback HTTP a `fan-identity-service`. Esta tarea implementa el lado que falta:

- `POST /internal/fans/notifications` — alta interna (la consume `alert-dispatcher-service`, T-30).
- `GET /api/fans/me/notifications` — el fan ve su propio historial.

Cumple el contrato ya congelado en T-01 (`docs/explicacion_contratos_api.md`, sección 2.3). No hay nada que decidir de diseño: es implementar ese contrato siguiendo el mismo patrón que T-12/T-13 (`Subscription` + `SubscriptionController`/`InternalFanController`).

## 2. Requisitos a satisfacer

1. **Entidad `Notification`:** guarda a qué fan pertenece (FK a `Fan`), el texto del mensaje, el evento que la originó (`eventId`, `matchId`, `teamId`) y la fecha.
2. **Alta interna** fuera de `/api/**`: se agrega al `InternalFanController` ya existente, junto al endpoint de suscriptores.
3. **Listado del fan** devuelve solo sus notificaciones, ordenadas de más nueva a más vieja.
4. **`fanId` inexistente** en el alta interna devuelve `404 FAN_NOT_FOUND` (contrato 2.3), no una excepción sin manejar.

**Decisión menor no cubierta explícitamente por el contrato:** el campo `createdAt` de la respuesta se llena con el `occurredAt` que manda el callback (cuándo ocurrió el evento deportivo), no con la hora de inserción en la base. Es la fecha que tiene sentido mostrarle al fan ("cuándo pasó esto"), y evita que un reintento o una demora en el fan-out corran la fecha mostrada. Si algún carril prefiere la hora de inserción, es un cambio de una línea en `NotificationService`.

## 3. Componentes a crear

- **`domain/Notification.java`** — entidad JPA, mismo estilo que `Subscription`: `@ManyToOne` a `Fan` (`fan_id`, `nullable = false`), más `message`, `matchId`, `teamId`, `eventId` (todos `String`, `nullable = false`) y `createdAt` (`Instant`, `nullable = false`). Tabla `notifications`.
- **`repository/NotificationRepository.java`** — `JpaRepository<Notification, Long>` con `List<Notification> findAllByFanIdOrderByCreatedAtDesc(Long fanId)`.
- **DTOs (`dto/`):**
  - `NotificationRequestDTO(String fanId, String eventId, String matchId, String teamId, String message, Instant occurredAt)` — body del alta interna.
  - `NotificationCreatedDTO(String id)` — respuesta `201` del alta interna.
  - `NotificationItemDTO(String id, String message, String matchId, String teamId, String eventId, Instant createdAt)` — items del listado del fan.
- **`service/NotificationService.java`** — mismo estilo que `SubscriptionService`:
  - `createNotification(Long fanId, String eventId, String matchId, String teamId, String message, Instant occurredAt)`: busca el `Fan` con `fanRepository.findById`, lanza `FanNotFoundException` (excepción anidada, como `TeamNotFoundException` en `SubscriptionService`) si no existe; guarda la `Notification` y devuelve el id.
  - `getNotifications(Long fanId)`: delega en el repositorio y mapea a `NotificationItemDTO`.
- **`controller/NotificationController.java`** — nuevo, `@RequestMapping("/api/fans/me/notifications")`, `GET` autenticado que resuelve el fan desde `@AuthenticationPrincipal String principal` (mismo patrón que `SubscriptionController`).
- **`controller/InternalFanController.java`** (existente) — se le agrega `NotificationService` al constructor y un `@PostMapping("/notifications")` que devuelve `201` con `NotificationCreatedDTO`.
- **`exception/GlobalExceptionHandler.java`** (existente) — se le agrega un `@ExceptionHandler(NotificationService.FanNotFoundException.class)` que devuelve `404 FAN_NOT_FOUND`, igual que el de `TeamNotFoundException`.

No hace falta tocar `cloud-gateway` (`/api/fans/**` ya rutea a `fan-identity-service`, `/internal/**` sigue sin ruta) ni `SecurityConfig` (`/internal/**` ya está en `permitAll()` desde T-13; `GET /api/fans/me/notifications` cae en `anyRequest().authenticated()`, que ya exige JWT válido).

## 4. Plan de verificación

1. `docker compose up -d --build fan-identity-service`.
2. Registrar/loguear un fan de prueba (T-11) y obtener su JWT y `fanId`.
3. Insertar una notificación por el endpoint interno, dentro de la red Docker: `docker compose exec alert-dispatcher-service curl -X POST http://fan-identity-service:8081/internal/fans/notifications -H "Content-Type: application/json" -d '{"fanId":"<id>","eventId":"evt-1","matchId":"match-001","teamId":"team-colon","message":"Gol de Colón","occurredAt":"2026-09-17T21:00:00Z"}'` → esperar `201` con `{"id": "..."}`.
4. `GET /api/fans/me/notifications` vía gateway con el JWT de ese fan → aparece la notificación insertada, con los campos del contrato.
5. Insertar una segunda notificación con `occurredAt` posterior y confirmar que el listado la devuelve primero (orden descendente).
6. Repetir el alta con un `fanId` inexistente (ej. `"999999"`) → `404` con `error: "FAN_NOT_FOUND"`.
7. Confirmar que `POST http://localhost:8080/internal/fans/notifications` (vía gateway, desde el host) da `404` del gateway por no existir la ruta — igual que en T-13.
