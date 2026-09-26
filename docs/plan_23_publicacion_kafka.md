# Plan de implementación — T-23 · Publicación del evento en Kafka

Tarea del Carril B, íntegramente en `match-ingestion-service`. Depende de T-21.

## 1. Contexto y Objetivos

Después de persistir un evento (T-21), `match-ingestion-service` lo publica en el tópico `match-events` con el esquema de la sección 5 del contrato (`docs/explicacion_contratos_api.md`). Esta tarea cubre el requisito obligatorio de mensajería asincrónica y desacopla la ingesta del despacho: `match-ingestion-service` no sabe quién consume el evento ni cuándo. El consumer es T-30.

## 2. Requisitos a satisfacer

1. **Tópico declarado explícitamente** con un bean `NewTopic`, sin depender de que el broker lo cree solo.
2. **Mensaje fiel al esquema:** el `value` es el `MatchEventDTO` de la sección 3.1, ya con `eventId`, y la `key` es el `teamId`.
   - En `GOAL` y `CARD` se publica **un** mensaje, con el `teamId` del evento.
   - En `MATCH_END` se publican **dos** mensajes con el mismo `eventId`: uno con el `teamId` local y otro con el visitante (contrato, sección 5).
3. **Kafka caído no rompe la ingesta:** el evento queda en Mongo, el fallo se loguea y el POST responde `201` igual.

### Decisiones de implementación (verificadas contra los jars del stack)

- **Serializer `JacksonJsonSerializer`.** En Spring Kafka 4.1, `JsonSerializer` está `@Deprecated` y usa Jackson 2. El reemplazo vigente es `JacksonJsonSerializer`, que usa Jackson 3, el mismo que trae Boot 4. Se verificó en `spring-kafka-4.1.0.jar`.
- **`spring.json.add.type.headers: false`.** Por defecto, el serializer agrega un header `__TypeId__` con el nombre de la clase Java (`com.tpnube.match_ingestion_service.dto.MatchEventDTO`), y esa clase no existe en `alert-dispatcher-service`. El contrato entre servicios es el JSON de T-01, no una clase Java: el consumer (T-30) decide él mismo a qué tipo deserializa.
- **`max.block.ms: 5000`.** Con Kafka caído, `send()` se queda bloqueado esperando la metadata del broker hasta `max.block.ms`, que por defecto es 60 s. Eso colgaría el POST un minuto; con 5 s, responde `201` en 5 s como mucho.
- **3 particiones y 1 réplica.** Con una sola partición, la clave no cambiaría nada. Con varias, Kafka garantiza el orden **por clave**: todos los eventos de un mismo equipo llegan en orden. Es 1 réplica porque hay un solo broker.
- **Nombre del tópico en `application.yaml`** (`topics.match-events`). T-30 declara la misma property en `alert-dispatcher-service`.

## 3. Componentes a crear o modificar

- **`application.yaml`**:
  ```yaml
  spring:
    kafka:
      bootstrap-servers: localhost:9092
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.springframework.kafka.support.serializer.JacksonJsonSerializer
        properties:
          spring.json.add.type.headers: false
          max.block.ms: 5000

  topics:
    match-events: match-events
  ```
  Cada property lleva un comentario con el *por qué* (sección 2). `SPRING_KAFKA_BOOTSTRAPSERVERS=kafka:19092` ya está en el compose, así que no hay que tocarlo.
- **`config/KafkaTopicConfig.java`**: un `@Bean NewTopic matchEventsTopic(@Value("${topics.match-events}") String name)` que devuelve `TopicBuilder.name(name).partitions(3).replicas(1).build()`. El `KafkaAdmin` que autoconfigura Boot lo crea al arrancar si no existe.
- **`kafka/MatchEventPublisher.java`**: un `@Component`. Recibe por constructor `KafkaTemplate<String, MatchEventDTO>` y `@Value("${topics.match-events}") String topic`.
  - El método `publish(MatchEventDTO event)` hace `kafkaTemplate.send(topic, event.teamId(), event)`.
  - Un fallo puede llegar por dos caminos distintos, y ninguno de los dos propaga la excepción:
    - **Sincrónico:** se supera `max.block.ms`. Se atrapa con `try/catch`.
    - **Asincrónico:** el broker no confirma la entrega. Se atrapa con `.whenComplete(...)` sobre el future.
  - En ambos casos hace `log.error` con `eventId`, `teamId` y la causa. Si sale bien, hace `log.info` con el tópico, la partición y el offset.
  - Un comentario explica el *por qué*: el evento ya quedó persistido, así que un fallo de publicación no debe convertirse en un error del POST (contrato, sección 5).
- **`service/MatchEventService.java`** (de T-21): al final de `ingestEvent`, después del `save` y de actualizar el partido:
  - `GOAL` o `CARD`: `publisher.publish(toDTO(event, event.getTeamId()))`.
  - `MATCH_END`: se publica dos veces, con `match.getHomeTeamId()` y con `match.getAwayTeamId()`. Un comentario remite a la sección 5 del contrato.

  El método de mapeo de T-22 recibe el `teamId` como parámetro para cubrir este caso.

No hacen falta dependencias nuevas: `spring-boot-starter-kafka` ya está en el `pom`.

## 4. Plan de verificación

1. `docker compose up -d --build match-ingestion-service cloud-gateway`.
2. Kafka UI (`:8090`) → Topics muestra `match-events` con 3 particiones **antes** de ingestar nada. Eso prueba que lo creó el `NewTopic` y no la auto-creación. Alternativa por consola: `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic match-events`.
3. Ingestar un `GOAL` de `team-colon` en `match-001` (curl de T-21). En Kafka UI → Messages tiene que verse:
   - `key` = `team-colon`.
   - `value` = JSON con exactamente los campos de la sección 3.1, y el mismo `eventId` que devolvió el POST.
   - `occurredAt` como string ISO-8601, no como número.
   - Sin header `__TypeId__`.

   Alternativa por consola: `kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic match-events --from-beginning --property print.key=true --property print.headers=true`.
4. Ingestar un `MATCH_END` en `match-001`: aparecen **dos** mensajes con el mismo `eventId`, uno con key `team-colon` y otro con key `team-union`.
5. Kafka caído:
   1. `docker compose stop kafka`.
   2. Ingestar un `GOAL`: responde `201` en unos 5 s como mucho, y el evento está en Mongo.
   3. En `docker compose logs match-ingestion-service` aparece el error de publicación. Puede salir enseguida (por `max.block.ms`) o hasta ~2 min después si el productor ya tenía la metadata del broker en cache (`delivery.timeout.ms`, default 120 s).
   4. `docker compose start kafka`.

   Ese evento no se republica. Es una limitación aceptada en la sección 5 del contrato: queda en Mongo y visible en `GET /api/matches/{id}/events`.
6. Agregar a `CLAUDE.md` un gotcha nuevo: en Spring Kafka 4 hay que usar `JacksonJsonSerializer`/`JacksonJsonDeserializer`, no los `Json*` deprecados, y desactivar los type headers entre servicios.
