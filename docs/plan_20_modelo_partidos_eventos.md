# Plan de implementación — T-20 · Modelo de documentos de partidos y eventos

Tarea del Carril B, en `match-ingestion-service`, más un ajuste de configuración en `docker-compose.yml` que es bloqueante (ver 1.1).

## 1. Contexto y Objetivos

Hoy `match-ingestion-service` tiene solamente la clase `@SpringBootApplication`. Esta tarea arma la base del carril B: los documentos Mongo `Match` y `MatchEvent`, sus repositorios y los datos semilla. No expone endpoints (eso es T-21 y T-22) ni publica en Kafka (T-23).

En esta tarea se ve por qué se eligió MongoDB. Un gol, una tarjeta y un fin de partido tienen campos distintos. Hay un solo documento `MatchEvent`, y los campos que no le corresponden a un tipo quedan en `null`. Como Spring Data MongoDB **no escribe los campos nulos**, cada documento guarda en la base solo lo que corresponde a su tipo: un `CARD` no tiene marcador y un `MATCH_END` no tiene jugador. En SQL esto obligaría a tener una tabla con columnas nulas o una tabla por tipo.

Por D-1, el catálogo de ligas y equipos **no** vive acá. Partidos y eventos referencian el equipo con `teamId` como string (`"team-colon"`). Es el mismo `Team.id` que siembra `fan-identity-service`, sin FK ni validación entre servicios.

### 1.1 Problema de configuración detectado (bloqueante)

En Spring Boot 4 la property de conexión `spring.data.mongodb.uri` cambió de nombre a `spring.mongodb.uri`. Se verificó en el metadata de `spring-boot-mongodb-4.1.0.jar`: la vieja figura como *deprecated* con nivel `error` y reemplazo `spring.mongodb.uri`. Eso significa que **ya no se bindea**.

Hoy `application.yaml` usa `spring.data.mongodb.uri` y `docker-compose.yml` usa `SPRING_DATA_MONGODB_URI`, así que las dos se ignoran y el driver cae en su default (`localhost:27017`, base `test`). T-02 no lo detectó porque el driver se conecta recién en la primera operación, y todavía no había ninguna. Con la semilla de esta tarea, dentro del contenedor el arranque fallaría por timeout contra un Mongo inexistente en `localhost`. Se corrige acá y se registra como R-8 en la planificación global.

## 2. Requisitos a satisfacer

1. **Eventos heterogéneos:** el documento de evento soporta `GOAL`, `CARD` y `MATCH_END` sin campos obligatorios que apliquen a un solo tipo.
2. **`teamId` como string:** partido y evento lo guardan con el mismo valor que `Team.id` de fan-identity (`team-colon`, `team-union`, `team-river`, `team-boca`, `team-racing`, `team-liverpool`, `team-manchester-city`).
3. **Semilla idempotente:** reiniciar el servicio no duplica nada.
4. **Conexión real al Mongo del compose:** se corrige la property (1.1).

**Decisión tomada: eventos semilla.** La tarea pide sembrar "un par de partidos". Se sembraron además 3 eventos de un partido terminado, uno de cada tipo. Hay dos motivos:

- T-22 depende solo de T-20, y sin eventos no hay forma de verificar "eventos de un partido en orden cronológico".
- Apenas termine esta tarea ya se puede ver en la base que los documentos son heterogéneos.

Los eventos semilla se guardan directo con el repositorio: no se publican en Kafka ni generan notificaciones.

## 3. Componentes a crear o modificar

Mismo estilo que `fan-identity-service`: Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor`) y paquetes `domain/`, `repository/` y `seed/`.

**Dominio (`domain/`):**

- **`MatchStatus.java`**: enum con `SCHEDULED`, `LIVE` y `FINISHED`, los valores del contrato (sección 3).
- **`EventType.java`**: enum con `GOAL`, `CARD` y `MATCH_END`.
- **`CardType.java`**: enum con `YELLOW` y `RED`.
- **`Match.java`**, con `@Document(collection = "matches")`:
  - `@Id String id`: se asigna a mano en la semilla (`"match-001"`, como en el ejemplo del contrato) para que se pueda leer fácil en los curl y en la demo.
  - `String homeTeamId`, `String awayTeamId`.
  - `MatchStatus status`, `Instant scheduledAt`.
  - `Integer homeScore`, `Integer awayScore`: son `Integer` y no `int` porque el contrato los admite `null` cuando el partido no empezó.
- **`MatchEvent.java`**, con `@Document(collection = "match_events")`:
  - `@Id String id`: ObjectId que genera Mongo. Es el `eventId` del contrato.
  - `String matchId`: referencia al partido por id, sin `@DBRef`, porque siempre se consulta por `matchId` y no hace falta cargar el partido.
  - `String teamId`: equipo al que pertenece el evento. Queda en `null` en `MATCH_END`, porque el fin del partido es de los dos equipos (ver T-21 y T-23).
  - `EventType type`, `Integer minute`, `String description`, `Instant occurredAt`.
  - Campos específicos, en `null` cuando no aplican: `String playerName` (`GOAL` y `CARD`), `CardType cardType` (`CARD`), `Integer homeScore` y `Integer awayScore` (`GOAL` y `MATCH_END`).
  - Un comentario en la clase explica el *por qué*: un solo documento para los tres tipos, y Mongo no persiste los nulos.

**Repositorios (`repository/`):**

- **`MatchRepository.java`**: `MongoRepository<Match, String>`. Las consultas por equipo se agregan en T-22.
- **`MatchEventRepository.java`**: `MongoRepository<MatchEvent, String>`. Las consultas por partido se agregan en T-22.

**Semilla (`seed/DataSeeder.java`):** un `ApplicationRunner`, con el mismo patrón que el de fan-identity (helpers `save...IfAbsent` que usan `existsById`).

| id | Local | Visitante | Estado | Marcador | `scheduledAt` |
|---|---|---|---|---|---|
| `match-001` | `team-colon` | `team-union` | `LIVE` | 0-0 | `2026-09-26T19:00:00Z` |
| `match-002` | `team-river` | `team-boca` | `SCHEDULED` | `null` | `2026-10-04T20:00:00Z` |
| `match-003` | `team-liverpool` | `team-manchester-city` | `FINISHED` | 1-0 | `2026-09-20T14:00:00Z` |

`match-001` es el partido "en vivo" para la demo: en T-21 se le cargan los goles. Los eventos de `match-003` tienen ids fijos (`seed-evt-001` a `seed-evt-003`) para que la idempotencia se pueda chequear con `existsById`, y un `occurredAt` coherente con `scheduledAt` + minuto:

- `GOAL`: `team-liverpool`, 34', `playerName` "Mohamed Salah", marcador 1-0.
- `CARD`: `team-manchester-city`, 60', `playerName` "Rodri", `RED`.
- `MATCH_END`: `teamId` `null`, 90', marcador 1-0.

**Configuración:**

- **`application.yaml`**: cambiar `spring.data.mongodb.uri` por `spring.mongodb.uri`, con el mismo valor.
- **`docker-compose.yml`**: cambiar `SPRING_DATA_MONGODB_URI` por `SPRING_MONGODB_URI`.
- **`docs/explicacion_docker_compose.md`**: agregar en la sección de `match-ingestion-service` una nota sobre el nombre de la variable y por qué cambió (regla de `CLAUDE.md`: el compose no se toca sin actualizar su explicación).
- **`CLAUDE.md`**: agregar un gotcha nuevo: en Boot 4 las properties de conexión de Mongo pasaron de `spring.data.mongodb.*` a `spring.mongodb.*`, y la vieja se ignora sin error.
- **`planificacion_global.md`**: marcar R-8 como resuelto una vez verificado.

No hacen falta dependencias nuevas: `spring-boot-starter-data-mongodb` y Lombok ya están en el `pom`.

## 4. Plan de verificación

1. `docker compose up -d --build match-ingestion-service`. Levanta mongo, kafka y eureka por `depends_on`.
2. `docker compose logs match-ingestion-service`: aparecen los logs de la semilla y ningún `MongoTimeoutException`.
3. `docker compose exec mongo mongosh match_ingestion_db --quiet --eval "db.matches.find()"` devuelve los 3 partidos. Esto prueba que la property nueva funciona: los datos están en el Mongo del compose, en `match_ingestion_db` y no en `test`.
4. `docker compose exec mongo mongosh match_ingestion_db --quiet --eval "db.match_events.find()"` devuelve los 3 eventos. El `CARD` no tiene `homeScore`/`awayScore` y el `MATCH_END` no tiene `playerName` ni `teamId`: es la evidencia del requisito 1. El campo `_class` lo agrega Spring Data y es normal.
5. `docker compose restart match-ingestion-service` y repetir los pasos 3 y 4 con `.countDocuments()`: tienen que seguir siendo 3 y 3 (idempotencia).
6. En el dashboard de Eureka (`:8761`), `MATCH-INGESTION-SERVICE` sigue `UP`.
