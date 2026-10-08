# Mensajería asincrónica con Kafka

Explica el requisito obligatorio de "mensajería asincrónica" del enunciado, implementado en T-23 (productor) y T-30 (consumer). Es el material para defenderlo en el coloquio.

## 1. Qué problema resuelve

Sin mensajería, `match-ingestion-service` tendría que llamarle directamente a `alert-dispatcher-service` cada vez que entra un evento, y esperar su respuesta. Eso acopla los dos servicios: si el despachador está lento o caído, la ingesta de eventos se cae con él, aunque ingestar y notificar sean responsabilidades distintas.

Con Kafka en el medio, `match-ingestion-service` publica el evento y sigue: le da lo mismo si hay cero, uno o diez consumidores, y si están arriba en ese momento o no. `alert-dispatcher-service` lee cuando puede. Es la misma idea que ya está en `CLAUDE.md` para el historial de alertas (callback HTTP con SSOT en fan-identity), pero acá el desacople es más fuerte: ni siquiera hay una llamada síncrona entre servicios.

## 2. El tópico y el esquema del evento

Contrato completo en `docs/explicacion_contratos_api.md`, sección 5. Resumen:

- **Tópico:** `match-events`.
- **Key de partición:** el `teamId` del equipo cuyos fans hay que notificar. Kafka garantiza orden **dentro de una partición**, y todos los mensajes con la misma key van siempre a la misma partición: así los hinchas de un equipo reciben los eventos en el orden en que ocurrieron, aunque el tópico tenga varias particiones.
- **Payload:** `MatchEventDTO`, el mismo shape que usan los endpoints REST de eventos (contrato 3.1). Es un único record para todos los tipos (`MATCH_START`, `GOAL`, `CARD`, `MATCH_END` y texto libre), con los campos que no aplican a un tipo en `null`. Su `teamId` es el equipo que hizo el evento.
- **Cada evento, dos mensajes (D-7, T-81):** un gol le importa a los hinchas de los dos equipos, así que `match-ingestion-service` publica cada evento **una vez por equipo del partido**: mismo `eventId` y mismo payload, la key del local en uno y la del visitante en el otro. El consumer busca los suscriptos del equipo de la key, sin casos especiales por tipo. Hasta T-81 esto se hacía solo con `MATCH_END`.

## 3. El productor (`match-ingestion-service`)

- **`config/KafkaTopicConfig.java`:** declara el tópico con un bean `NewTopic` (3 particiones, 1 réplica) en vez de depender de la auto-creación del broker. Se puede confirmar en Kafka UI (`:8090` → Topics) que el tópico ya existe **antes** de ingestar el primer evento.
- **`kafka/MatchEventPublisher.java`:** envuelve el `KafkaTemplate` y expone un único método, `publish(notifiedTeamId, event)`: el primer parámetro es la key. `MatchEventService` lo llama dos veces por evento (local y visitante), después de persistir el evento en Mongo, nunca antes: si Kafka está caído, el evento igual queda guardado y el POST responde `201`; el fallo de publicación solo se loguea (contrato, sección 5).
- **Serialización:** `application.yaml` usa `JacksonJsonSerializer` (no `JsonSerializer`, deprecado en Spring Kafka 4 — ver gotcha en `CLAUDE.md`) y desactiva `spring.json.add.type.headers`. Sin eso, cada mensaje llevaría un header `__TypeId__` con el nombre completo de la clase Java del productor (`com.tpnube.match_ingestion_service.dto.MatchEventDTO`), que no existe con ese nombre en `alert-dispatcher-service`. El contrato entre los dos servicios es el JSON de la sección 5, no una clase Java compartida.
- **`max.block.ms: 5000`:** si Kafka está caído, `send()` por defecto se queda esperando hasta 60 s la metadata del broker antes de fallar. Con este valor, el POST de ingesta no se cuelga más de 5 s en ese escenario.

## 4. El consumidor (`alert-dispatcher-service`)

- **`listener/MatchEventListener.java`:** un `@KafkaListener` delgado a propósito. Recibe el `ConsumerRecord` entero (key y value), loguea el evento y delega todo en `AlertDispatchService.dispatch(event, key)`, que es una clase de Spring normal (no depende de Kafka) y por eso se puede testear con Mockito sin levantar un broker (`AlertDispatchServiceTest`).
- **Deserialización:** `ErrorHandlingDeserializer` envolviendo a `JacksonJsonDeserializer`. Sin el `ErrorHandlingDeserializer`, un mensaje que no es JSON válido (por ejemplo, publicado a mano desde Kafka UI) rompe la deserialización en el `poll()`, **antes** de llegar al listener: el consumer reintenta ese mismo mensaje para siempre y la partición queda bloqueada. Con él, el error se loguea y se pasa al siguiente mensaje.
- **`spring.json.value.default.type` + `spring.json.use.type.headers: false`:** el consumer siempre deserializa al `MatchEventDTO` propio de `alert-dispatcher-service` (una copia del DTO, no la misma clase), sin depender de headers de tipo que el productor no manda.
- **`group-id: alert-dispatcher-service` + `auto-offset-reset: earliest`:** la primera vez que este grupo se conecta, lee el tópico desde el principio (no pierde eventos publicados antes de que el servicio estuviera arriba). Las veces siguientes, sigue desde el último offset que confirmó.
- **`AlertDispatchService.dispatch(event, notifiedTeamId)`:** resuelve los suscriptores del equipo de la key (a través del Circuit Breaker, ver `explicacion_circuit_breaker.md`), arma un texto legible según el tipo de evento y hace un `POST /internal/fans/notifications` por fan. La notificación guarda los dos equipos: el que sigue el fan (la key) y el que hizo el evento (el `teamId` del payload), para que `/alerts` muestre bien un gol del rival. Un fallo notificando a un fan no corta el loop de los demás, y un mensaje sin key se registra como no despachado.

## 5. Cómo verlo funcionando

1. `docker compose up -d`.
2. Kafka UI (`http://localhost:8090`) → **Topics** → `match-events` ya existe, con 3 particiones, antes de ingestar nada (confirma que lo creó el `NewTopic`, no la auto-creación).
3. Ingestar un evento (`POST /api/matches/{matchId}/events` vía gateway, ver `docs/plan_21_ingesta_eventos.md`).
4. Kafka UI → `match-events` → **Messages**: aparecen dos mensajes con el mismo `value` (el JSON del evento, sin header `__TypeId__`) y distinta `key`: el `teamId` del local y el del visitante.
5. Kafka UI → **Consumers**: el grupo `alert-dispatcher-service` muestra el offset consumido al día.
6. Logs de `alert-dispatcher-service`: se ve "Evento recibido" dos veces, una para los fans de cada equipo, y el resumen de cuántas notificaciones se crearon en cada caso.
7. Para la demo de resiliencia del productor: `docker compose stop kafka`, ingestar un evento (igual responde `201`, queda en Mongo), y en los logs de `match-ingestion-service` aparece el error de publicación sin que el POST haya fallado.
8. Para la demo de resiliencia del consumer: Kafka UI → **Produce Message** → publicar el value `hola` (no es JSON) en `match-events`. El despachador loguea un error de deserialización y el siguiente mensaje válido se sigue procesando con normalidad.

## 6. Limitaciones aceptadas

- **Sin reintento ni Dead Letter Topic:** un mensaje que no se pudo despachar (ver `explicacion_circuit_breaker.md`) no vuelve a intentarse solo.
- **Entrega *at-least-once*, no *exactly-once*:** si el proceso se cae justo después de despachar pero antes de confirmar el offset, ese evento se reprocesa al reiniciar. No se implementó idempotencia del lado del consumer porque el enunciado no lo exige y el TP no tiene forma de demostrar la diferencia sin inyectar fallas específicas.
- **Un solo consumer por grupo:** no se configuró concurrencia (`concurrency` en el listener), así que un evento lento en despachar demora a los que le siguen en la misma partición. Ver `explicacion_circuit_breaker.md` sección "Limitaciones" para el caso puntual del callback de notificación.
