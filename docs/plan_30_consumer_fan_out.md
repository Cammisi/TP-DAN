# Plan de implementación — T-30 · Consumer Kafka y orquestación del fan-out

Tarea del Carril B, íntegramente en `alert-dispatcher-service`. Depende de T-23 (productor), y de T-13 y T-14 (endpoints internos de fan-identity, ya hechos).

## 1. Contexto y Objetivos

Esta tarea es el corazón del sistema: convierte un evento deportivo en alertas personalizadas. El flujo es:

1. Se consume `match-events` (contrato, sección 5).
2. Se resuelven los suscriptos del `teamId` con `FanIdentityLookupService`, que llama a `GET /internal/fans/subscribers/{teamId}` a través del Circuit Breaker. Ya existe desde T-13.
3. Por cada fan se persiste una notificación con `POST /internal/fans/notifications` (contrato 2.3, callback de D-3). `alert-dispatcher-service` no tiene base propia.

> **Nota post-implementación:** T-30 y T-31 se implementaron juntas, así que `dispatch()` ya incluye desde el inicio el manejo de `SubscribersUnavailableException` y el registro de eventos no despachados que este plan describía como alcance de T-31. El diseño final de ese fallback está en [plan_31](plan_31_circuit_breaker_estado.md).

## 2. Requisitos a satisfacer

1. El listener deserializa el evento con el esquema de T-01.
2. Se genera una notificación por cada fan suscripto, con un texto legible armado a partir del evento.
3. Un evento sin suscriptos se procesa sin error y queda logueado.
4. Si falla la notificación de un fan, se sigue notificando a los demás.

### Decisiones de implementación (verificadas contra los jars del stack)

- **`ErrorHandlingDeserializer` que delega en `JacksonJsonDeserializer`.** En Spring Kafka 4.1, `JsonDeserializer` está deprecado (usa Jackson 2).
  - Por qué hace falta el `ErrorHandlingDeserializer`: si llega un mensaje que no es JSON válido (por ejemplo, uno publicado a mano desde Kafka UI), sin él la deserialización falla en el `poll()`, antes de llegar al listener. El consumer reintentaría ese mismo mensaje para siempre y bloquearía la partición. Con él, el error se loguea y el mensaje se saltea.
- **`spring.json.value.default.type` + `spring.json.use.type.headers: false`.** El consumer deserializa siempre a su propio `MatchEventDTO`, sin depender de headers de tipo Java (T-23 no los manda). El contrato es el JSON.
- **`group-id: alert-dispatcher-service` y `auto-offset-reset: earliest`.** La primera vez que arranca el grupo lee el tópico desde el principio, así no se pierden eventos publicados antes de que el despachador estuviera arriba. Después sigue desde el último offset confirmado.
- **El callback se agrega al `FanIdentityClient` existente.** Es el mismo servicio destino, así que queda un cliente Feign por servicio.
  - El callback **no** pasa por el Circuit Breaker, que protege la consulta de suscriptores. Si fan-identity está caído, esa consulta falla primero y los callbacks ni se intentan. Si fan-identity solo tiene degradada la **escritura** (la consulta de suscriptores sigue respondiendo rápido), el callback sí se intenta sin la protección del circuito; para que eso no cuelgue el hilo del listener, el cliente Feign tiene un timeout corto propio (`spring.cloud.openfeign.client.config.fan-identity-service` en `application.yaml`; se había escrito con el prefijo viejo `feign.client`, que se ignora, y se corrigió en T-41 — R-10) en vez del default de 60 s.
- **Texto de la notificación.** Lo arma el despachador según el tipo. No incluye nombres de equipo, porque el despachador no tiene catálogo (D-1); la notificación lleva el `teamId` y el frontend muestra el nombre.
  - `GOAL`: `"¡Gol! Facundo Farías (78') — marcador 1-0"`.
  - `CARD`: `"Tarjeta roja para Rodri (60')"`, o `"Tarjeta amarilla para …"`.
  - `MATCH_END`: `"Final del partido — resultado 1-0"`.
  - Tipo desconocido: se usa la `description` si viene, o si no `"Nuevo evento en el partido <matchId>"`.
- **Logs:** se loguea el `fanId` pero nunca el email, que es dato personal (`CLAUDE.md`).

## 3. Componentes a crear o modificar

- **`dto/MatchEventDTO.java`**: `record` idéntico al de la sección 3.1 del contrato. Es una copia: cada servicio tiene la suya y lo que se comparte es el JSON.
- **`client/dto/NotificationRequestDTO.java`**: `record(String fanId, String eventId, String matchId, String teamId, String message, Instant occurredAt)` (contrato 2.3).
- **`client/dto/NotificationCreatedDTO.java`**: `record(String id)`.
- **`client/FanIdentityClient.java`**: se agrega
  ```java
  @PostMapping("/internal/fans/notifications")
  NotificationCreatedDTO createNotification(@RequestBody NotificationRequestDTO request);
  ```
- **`service/AlertDispatchService.java`** (nuevo): recibe por constructor `FanIdentityLookupService` y `FanIdentityClient`. El método `dispatch(MatchEventDTO event)`:
  1. Obtiene `subscribers = fanIdentityLookupService.getSubscribers(event.teamId())`.
  2. Si la lista viene vacía, hace `log.info` ("evento X del equipo Y sin suscriptos") y termina.
  3. Arma el texto con `buildMessage(event)`, un método privado con un `switch` sobre `type`.
  4. Recorre los suscriptos. Para cada uno llama a `fanIdentityClient.createNotification(...)` dentro de un `try/catch (RuntimeException e)`; si falla, hace `log.error` con `fanId`, `eventId` y `e.getMessage()` y cuenta el fallo.
     - Un comentario explica el catch amplio: Feign lanza tipos distintos (error HTTP, error de conexión, sin instancias en Eureka) y ninguno debe cortar el loop. No se traga nada, porque cada fallo queda logueado con el fan y el evento.
  5. Termina con `log.info` del resumen: cuántas notificaciones se crearon y cuántas fallaron.
- **`listener/MatchEventListener.java`**: un `@Component` con `@KafkaListener(topics = "${topics.match-events}")` (el grupo sale del yaml). Loguea `eventId`, `type` y `teamId`, y delega en `alertDispatchService.dispatch(event)`. Es delgado a propósito: toda la lógica queda en el servicio, que se puede testear sin Kafka.
- **`application.yaml`**:
  ```yaml
  spring:
    kafka:
      bootstrap-servers: localhost:9092
      consumer:
        group-id: alert-dispatcher-service
        auto-offset-reset: earliest
        key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
        value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
        properties:
          spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JacksonJsonDeserializer
          spring.json.value.default.type: com.tpnube.alert_dispatcher_service.dto.MatchEventDTO
          spring.json.use.type.headers: false

  topics:
    match-events: match-events
  ```
  Cada property lleva un comentario con el *por qué*.
- **Test unitario `src/test/java/.../service/AlertDispatchServiceTest.java`**: usa Mockito, sin contexto de Spring (Mockito ya viene con los starters de test del `pom`). Casos cubiertos (el último se agregó en T-31, tras una revisión de código):
  - Con dos suscriptos, el client falla con el primero, y se verifica que `createNotification` se llamó también para el segundo.
  - Sin suscriptos, no se llama nunca a `createNotification`.
  - Falla la consulta de suscriptores (circuito abierto o fan-identity caído): se registra como no despachado.
  - Fallan **todos** los callbacks de un evento: también se registra como no despachado, no como procesado.

  **Por qué el test forma parte de esta tarea:** el requisito 4 no se puede reproducir a mano. Todos los suscriptos que devuelve el endpoint son fans válidos, así que no hay forma de hacer fallar a uno solo, y `CLAUDE.md` exige verificar ejecutando. Además suma a T-70.

No hacen falta dependencias nuevas: Kafka, OpenFeign y Resilience4J ya están en el `pom`. Tampoco se tocan el compose ni el gateway.

## 4. Plan de verificación

1. `docker compose up -d --build match-ingestion-service alert-dispatcher-service cloud-gateway`.
2. Login de `fan1@quienjuega.com` / `fan123` por el gateway para obtener el JWT, y `POST /api/fans/subscriptions` con `{"teamId":"team-colon"}`.
3. En Kafka UI → Consumers aparece el grupo `alert-dispatcher-service` suscripto a `match-events`.
4. Ingestar un `GOAL` de `team-colon` en `match-001` (curl de T-21). En los logs de `alert-dispatcher-service` aparece "evento recibido" y el resumen "1 creadas, 0 fallidas".
5. `GET /api/fans/me/notifications` con el JWT de fan1 muestra la notificación nueva: texto legible, `matchId` `match-001`, `teamId` `team-colon` y el `eventId` que devolvió el POST. **Este es el criterio de *Terminada cuando*.**
6. Dos fans: loguear a `fan2`, suscribirlo a `team-colon`, ingestar otro evento y confirmar que los dos lo ven.
7. Sin suscriptos: ingestar un evento de `team-union`, que nadie sigue. El log dice "sin suscriptos" y no hay stacktraces.
8. `MATCH_END` en `match-001`: los fans de `team-colon` reciben "Final del partido".
9. Test unitario. En esta máquina no hay Maven ni JDK 25 (R-3), así que se corre con Docker desde la raíz del repo y tiene que dar verde:
   ```
   docker run --rm -v "${PWD}/backend-app/microservices/alert-dispatcher-service:/app" -w /app maven:3.9-eclipse-temurin-25 mvn -B -q test -Dtest=AlertDispatchServiceTest
   ```
10. Mensaje malformado: en Kafka UI → Produce Message, publicar en `match-events` el value `hola`. El despachador loguea **un** error de deserialización, y el siguiente evento válido se procesa normalmente (el consumer no quedó bloqueado).
