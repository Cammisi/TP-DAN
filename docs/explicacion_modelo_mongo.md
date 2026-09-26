# Modelo de documentos en `match-ingestion-service`

Explica el requisito obligatorio de "1 base SQL + 1 base NoSQL" del enunciado, del lado NoSQL. Implementado en T-20. Es el material para justificar en el coloquio por qué este microservicio usa MongoDB y no Postgres.

## 1. Por qué Mongo acá y no en fan-identity

El informe eligió PostgreSQL para `fan-identity-service` porque necesita **integridad referencial real**: una suscripción no puede existir sin un fan y un equipo válidos (FK), y eso es exactamente lo que un motor relacional garantiza mejor.

`match-ingestion-service` tiene el problema inverso: un partido tiene eventos, pero un gol, una tarjeta y el fin de partido **no comparten los mismos campos**. Modelarlos en SQL obligaría a elegir entre:

- una tabla con columnas que quedan `NULL` para la mayoría de las filas (una tabla `events` con `player_name`, `card_type`, `home_score`, `away_score`, donde cada fila usa 2-3 de esos 4 campos y el resto queda vacío), o
- una tabla por tipo de evento con joins o `UNION` para leer el historial completo de un partido en orden.

Ninguna de las dos es mejor que un documento por evento con solo los campos que le corresponden. Esa es la justificación real de "documento flexible" que pide el enunciado, no una elección arbitraria.

## 2. El modelo: `Match` + `MatchEvent`

Dos colecciones (`matches` y `match_events`), sin relación declarada entre ellas a nivel de Mongo (no hay `@DBRef`): `MatchEvent.matchId` es un string que apunta al `_id` de un `Match`, resuelto a mano en el código (`matchRepository.existsById(matchId)`), no por el motor.

- **`Match`**: `id` (asignado a mano, ej. `"match-001"`, no un `ObjectId` generado), `homeTeamId`, `awayTeamId`, `status` (`SCHEDULED`/`LIVE`/`FINISHED`) y el marcador (`Integer`, no `int`, porque un partido que no arrancó no tiene marcador).
- **`MatchEvent`**: un único documento para los tres tipos. Los campos que no aplican a un tipo quedan en `null`, y Spring Data Mongo **no persiste los campos `null`** — por eso un documento `CARD` en la base literalmente no tiene las claves `homeScore`/`awayScore`, no las tiene en `0` ni vacías. Se puede confirmar con `mongosh`:

  ```js
  db.match_events.findOne({ type: "CARD" })
  // no aparecen homeScore ni awayScore en el resultado
  ```

## 3. `teamId` como string, sin catálogo propio (D-1)

Por la decisión D-1, el catálogo de ligas y equipos (`League`, `Team`) vive en `fan-identity-service`/PostgreSQL, no acá. `match-ingestion-service` guarda `teamId` como un string plano (`"team-colon"`) sin validarlo contra ningún catálogo — es el mismo valor que usa el `Team.id` de fan-identity y el que viaja en los eventos de Kafka, pero la relación es una convención entre servicios, no una FK.

Esto es una **referencia débil entre microservicios**, normal en una arquitectura donde cada servicio es dueño de sus propios datos: `match-ingestion-service` no llama a `fan-identity-service` para validar que un `teamId` exista, porque eso acoplaría un microservicio de solo-lectura-de-eventos a la disponibilidad de otro, por una validación que no es crítica (T-21 solo valida que el equipo juegue *ese* partido según su propia base, no que exista en el catálogo global).

## 4. La semilla

`seed/DataSeeder.java` sigue el mismo patrón que el de `fan-identity-service`: un `ApplicationRunner` con helpers `save...IfAbsent` que usan `existsById` antes de guardar, así reiniciar el servicio no duplica nada. Siembra 3 partidos (uno `LIVE`, uno `SCHEDULED`, uno `FINISHED`) y, para el partido terminado, sus 3 eventos con ids fijos (`seed-evt-001` a `seed-evt-003`) — uno de cada tipo, para que se pueda ver la heterogeneidad de los documentos desde el primer arranque, sin depender de ingestar nada a mano.

## 5. El gotcha real que costó tiempo (R-8)

Spring Boot 4 renombró la property de conexión: `spring.data.mongodb.uri` pasó a ser `spring.mongodb.uri`. La vieja quedó deprecada a nivel `error` y **ya no se bindea** — no tira ningún warning al arrancar, simplemente el driver ignora la variable de entorno del compose y cae en su default (`localhost:27017`). Detalle completo en el gotcha de `CLAUDE.md`. Vale mencionarlo en el coloquio: es el tipo de bug silencioso que solo aparece cuando alguien intenta conectarse de verdad, y por eso T-02 no lo detectó (el servicio no tenía ni un repositorio que disparara una consulta).

## 6. Cómo verificarlo

1. `docker compose up -d match-ingestion-service mongo eureka-server`.
2. `docker compose exec mongo mongosh match_ingestion_db --eval "db.matches.find()"` → los 3 partidos semilla.
3. `docker compose exec mongo mongosh match_ingestion_db --eval "db.match_events.find()"` → los 3 eventos, con distintos campos presentes según el `type`.
4. `docker compose restart match-ingestion-service` y repetir el paso 2/3 con `.countDocuments()`: siguen siendo 3 y 3 (idempotencia).
