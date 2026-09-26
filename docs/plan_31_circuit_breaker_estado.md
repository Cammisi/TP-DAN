# Plan de implementación — T-31 · Cerrar el Circuit Breaker y exponer su estado

Tarea del Carril B, íntegramente en `alert-dispatcher-service`, más un documento `explicacion_*`. Depende de T-30.

## 1. Contexto y Objetivos

Resilience4J es la característica extra elegida para el TP. Hoy el fallback de `FanIdentityLookupService` devuelve una lista vacía con un `TODO`. Con T-30 funcionando, eso significa que si fan-identity está caído, el despachador trata el evento como "sin suscriptos" y lo pierde en silencio. Es justo lo que no hay que mostrar en el coloquio.

Esta tarea hace tres cosas:

1. Cambia el fallback para que distinga "no hay suscriptos" de "no pude averiguar quiénes son", y deja registro de los eventos no despachados.
2. Implementa `GET /api/alerts/status` (D-6, contrato sección 4) para el panel de admin (T-65).
3. Documenta cómo demostrar la apertura del circuito.

## 2. Requisitos a satisfacer

1. El fallback deja registro de los eventos no despachados en vez de descartarlos.
2. Hay un endpoint de estado con los últimos eventos procesados y el estado del circuito.
3. `docs/explicacion_circuit_breaker.md` explica cómo demostrar la apertura del circuito.

### Diseño

- **El fallback lanza una excepción en vez de devolver una lista vacía.**
  1. Primero loguea un `WARN` que distingue la causa. Si `throwable instanceof CallNotPermittedException`, el circuito está abierto y no se llamó a fan-identity. Si no, la consulta falló y se loguea la causa.
  2. Después lanza `SubscribersUnavailableException`, una excepción anidada en `FanIdentityLookupService`.

  El fallback sigue teniendo sentido: es el lugar donde el fallo del circuito se traduce a un error de dominio, con un log que tiene contexto (`teamId` y causa). Sin fallback, `run()` lanzaría `NoFallbackAvailableException` con la causa envuelta.
- **`AlertDispatchService.dispatch` (de T-30) atrapa `SubscribersUnavailableException`.**
  - Si la atrapa, registra el evento como no despachado y hace `log.warn` con `eventId`, `matchId` y `teamId`.
  - Si el despacho sale bien, incluso con 0 suscriptos, lo registra como procesado con su `subscriberCount`.
  - El mensaje de Kafka se da por consumido. El "registro" que pide la tarea es el log (que en T-51 queda centralizado en Loki) más la lista del endpoint de estado.
  - El **reintento automático** de los no despachados queda fuera de alcance. Si el grupo lo quiere, se propone como tarea nueva; por ejemplo, con un tópico `match-events.DLT` visible en Kafka UI.
- **Registro en memoria.** Se guardan los últimos 20 procesados y los últimos 20 no despachados, más nuevos primero. Los métodos son `synchronized` porque el hilo del listener de Kafka escribe mientras los hilos HTTP leen. Se pierde al reiniciar el servicio y queda documentado como limitación.
- **`subscriberCount` refleja notificados con éxito, no suscriptos totales.** Si el callback `POST /internal/fans/notifications` falla para alguno de los fans, `AlertDispatchService` no cuenta esos fallos como "procesados": `subscriberCount` es `subscribers.size() - failures`. Si **todos** los callbacks de un evento fallan, se registra como no despachado (`recordUndispatched`, con el motivo) en vez de como procesado con `subscriberCount: 0` — si no, el endpoint de estado ocultaría justo el escenario que esta tarea se propuso dejar de ocultar (encontrado en revisión de código sobre la primera versión de este plan, que solo contemplaba el fallo de la *consulta* de suscriptores, no el del *callback* por fan).
- **Estado del circuito.**
  - Se inyecta el bean `CircuitBreakerRegistry`. Se verificó que `Resilience4JCircuitBreakerFactory` se construye sobre ese registry, así que el circuito que usa `FanIdentityLookupService` vive ahí.
  - Se consulta con `registry.find(CIRCUIT_BREAKER_NAME)`. Si todavía no existe (no se procesó ningún evento), se informa `"CLOSED"`.
  - **Gotcha:** hay que usar `find(...)` y no `circuitBreaker(...)`. `circuitBreaker(...)` crea el circuito si no existe, con la config default del registry, y después la factory reutilizaría ese circuito ignorando `CircuitBreakerConfiguration`.
- **El contrato crece.** La respuesta suma `undispatchedEvents`. La sección 4 del contrato lo permite ("puede crecer si esa tarea lo necesita"); se actualiza `docs/explicacion_contratos_api.md`.

**Decisión a aprobar: `minimumNumberOfCalls(5)` en `CircuitBreakerConfiguration`.**

- Resilience4J necesita un mínimo de llamadas antes de calcular la tasa de fallos. Por defecto son 100, pero en una ventana por conteo queda acotado al tamaño de la ventana (`CircuitBreakerMetrics` hace `Math.min`, verificado en `resilience4j-circuitbreaker-2.3.0.jar`). Con la config actual, entonces, hacen falta **10** eventos fallidos para abrir el circuito.
- Con el mínimo explícito en 5, la demo necesita 5 eventos y el comportamiento no depende de un detalle implícito de la librería.

**Seguridad:** el contrato dice que `/api/alerts/status` es solo para admin. Igual que en R-7, hoy nadie exige el rol: por D-2, alert-dispatcher no tiene Spring Security. Se resuelve en T-40 junto con R-7.

## 3. Componentes a crear o modificar

- **`service/FanIdentityLookupService.java`**:
  - `CIRCUIT_BREAKER_NAME` pasa a ser `public static final`, para que el servicio de estado use el mismo nombre.
  - Se reemplaza el fallback según el diseño.
  - Se agrega la excepción anidada `SubscribersUnavailableException`.
  - Se saca el `TODO`.
- **`service/AlertDispatchService.java`** (de T-30):
  - Recibe `DispatcherStatusService` por constructor.
  - Atrapa `SubscribersUnavailableException` y registra procesados y no despachados.
- **`service/DispatcherStatusService.java`** (nuevo):
  - `recordProcessed(MatchEventDTO event, int subscriberCount)`.
  - `recordUndispatched(MatchEventDTO event, String reason)`.
  - `getStatus()`, que devuelve un `DispatcherStatusDTO`.
  - Recibe `CircuitBreakerRegistry` por constructor.
- **DTOs (`dto/`)**:
  - `DispatcherStatusDTO(String circuitState, List<ProcessedEventDTO> lastProcessedEvents, List<UndispatchedEventDTO> undispatchedEvents)`.
  - `ProcessedEventDTO(String eventId, String matchId, String teamId, Instant dispatchedAt, int subscriberCount)`: son los campos del contrato 4.
  - `UndispatchedEventDTO(String eventId, String matchId, String teamId, Instant failedAt, String reason)`.
- **`controller/DispatcherStatusController.java`** (nuevo): `@RequestMapping("/api/alerts")` con `@GetMapping("/status")`. La ruta `/api/alerts/**` ya está en el gateway.
- **`config/CircuitBreakerConfiguration.java`**: se agrega `.minimumNumberOfCalls(5)`.
- **`AlertDispatchServiceTest`** (de T-30): se agrega el mock de `DispatcherStatusService` al constructor y un caso nuevo. Cuando el lookup lanza `SubscribersUnavailableException`, se registra como no despachado y no se llama a `createNotification`.
- **`docs/explicacion_contratos_api.md`**: la sección 4 suma `undispatchedEvents`.
- **`docs/explicacion_circuit_breaker.md`** (nuevo):
  1. Qué problema resuelve.
  2. Los estados `CLOSED`/`OPEN`/`HALF_OPEN`, con los valores configurados y por qué: ventana de 10, 50 % de fallos, 15 s abierto, 3 llamadas de prueba, timeout de 3 s, mínimo de 5.
  3. Dónde está en el código.
  4. Qué hace el fallback.
  5. La demo paso a paso (la de la sección 4 de este plan).
  6. Limitaciones:
     - Los no despachados no se reintentan.
     - El registro en memoria se pierde al reiniciar (queda el log).
     - El paso de `OPEN` a `HALF_OPEN` no es automático: ocurre con la primera llamada después de los 15 s, así que el estado sigue mostrando `OPEN` hasta que llegue un evento nuevo.

No hacen falta dependencias nuevas.

## 4. Plan de verificación

Todo va por el gateway. Si T-40 ya está hecha, conseguí el token **antes** de bajar fan-identity, porque sin él no hay login.

1. Stack arriba y `fan1` suscripto a `team-colon` (de T-30).
2. `GET http://localhost:8080/api/alerts/status` devuelve `circuitState: "CLOSED"`, `lastProcessedEvents` con lo ya procesado y `undispatchedEvents: []`.
3. Ingestar un evento normal: aparece primero en `lastProcessedEvents` con `subscriberCount: 1`.
4. `docker compose stop fan-identity-service`.
5. Ingestar 6 `GOAL` de `team-colon` en `match-001`. Cada POST responde `201`, porque match-ingestion no depende de fan-identity.
6. Logs de `alert-dispatcher-service`: los primeros eventos muestran "falló la consulta de suscriptores" y, cuando se alcanza el mínimo de llamadas, "circuito abierto, no se consultó fan-identity".
7. `GET /api/alerts/status` devuelve `OPEN`, con los 6 eventos en `undispatchedEvents` y su motivo. alert-dispatcher sigue respondiendo con normalidad.
8. `docker compose start fan-identity-service`. Esperar a que aparezca `UP` en Eureka (unos 30 a 60 s) y a que pasen al menos 15 s desde la apertura.
9. Ingestar 3 eventos más. Con el primero el circuito pasa a `HALF_OPEN`, y con las 3 llamadas de prueba exitosas vuelve a `CLOSED`. El status muestra `CLOSED` y `fan1` ve esas 3 notificaciones, pero no las 6 no despachadas (limitación documentada).
10. Test unitario con el comando de T-30 (`-Dtest=AlertDispatchServiceTest`), en verde.
11. **Criterio de *Terminada cuando*:** otro integrante repite los pasos 4 a 9 siguiendo **solo** `docs/explicacion_circuit_breaker.md` y obtiene el mismo resultado.
