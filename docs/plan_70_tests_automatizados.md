# Plan de implementación — T-70 · Pruebas automatizadas mínimas

Transversal, sobre los 5 servicios Spring.

## 1. Qué

Cubrir lo que pide la tarea, sin inventar cobertura de más: un test de contexto por servicio (ya existían los 5) más las reglas de negocio puntuales que el enunciado marca como críticas.

## 2. Estado de partida

Ya existían, de tareas anteriores:
- Test de contexto (`@SpringBootTest` + `contextLoads()`) en los 5 servicios.
- `alert-dispatcher-service`: `AlertDispatchServiceTest` (Mockito) ya cubre el fallback del Circuit Breaker con 4 casos (fallo parcial, fallo total, sin suscriptos, circuito abierto) — de T-31, no hace falta agregar nada.

Faltaba:
- `fan-identity-service`: registro/login, no duplicar suscripciones.
- `match-ingestion-service`: rechazo de evento inválido.

## 3. Componentes creados

- `fan-identity-service/src/test/java/.../service/AuthServiceTest.java`: registro exitoso, email duplicado, login correcto, contraseña incorrecta, email inexistente. Mockea `FanRepository`/`JwtService`, usa un `BCryptPasswordEncoder` real (no mockeado) para que el test de login dependa de un hash/verificación consistentes de verdad, no de un mock que siempre da bien.
- `fan-identity-service/src/test/java/.../service/SubscriptionServiceTest.java`: seguir el mismo equipo dos veces no duplica (`isNew() == false`, `save` nunca llamado), seguir un equipo nuevo sí crea la fila, seguir un equipo inexistente rechaza.
- `match-ingestion-service/src/test/java/.../service/MatchEventServiceTest.java`: evento sin `type`, sin `minute`, con un `type` no soportado, contra un partido inexistente, y un `GOAL` sin `playerName` — los cinco rechazan sin llegar a `matchEventRepository.save`.

Mismo estilo que `AlertDispatchServiceTest` (Mockito puro sobre el service, sin levantar contexto de Spring): más rápido y no necesita Testcontainers/base real para probar reglas de negocio que no dependen de SQL/consultas complejas.

## 4. Plan de verificación

`mvn test` no se puede correr localmente (sin Maven/JDK en esta máquina, ver gotcha de `CLAUDE.md`). Se corre vía Docker: contenedor `maven:3.9-eclipse-temurin-25` montando el código de cada servicio, con la infraestructura real (`postgres`, `mongo`, `kafka`, `eureka-server`) ya levantada y las mismas variables de entorno que usa `docker-compose.yml`, para que los tests de contexto conecten de verdad. Se corre para los 5 servicios; la tarea queda terminada cuando los 5 `mvn test` terminan en verde.
