# Circuit Breaker en `alert-dispatcher-service`

Explica la característica extra elegida para el TP (Resilience4J vía Spring Cloud CircuitBreaker), implementada en T-13, T-30 y T-31. Es el material para defenderla en el coloquio, incluida la demo de apertura del circuito.

## 1. Qué problema resuelve

`alert-dispatcher-service` depende de `fan-identity-service` para saber a quién notificar (`GET /internal/fans/subscribers/{teamId}`, contrato T-01 sección 2.5). Si fan-identity está caído o muy lento, sin protección cada evento de Kafka dispararía una llamada HTTP que tarda en fallar (timeout de conexión, reintentos del cliente HTTP), y esas llamadas se acumulan: el despachador se queda esperando en vez de seguir procesando eventos.

El Circuit Breaker corta ese camino: después de una cantidad de fallos, deja de intentar la llamada real durante un tiempo y falla rápido, para que el resto del sistema no se vea arrastrado por una dependencia caída.

## 2. Estados y configuración

`CircuitBreakerConfiguration` (`alert-dispatcher-service`) define un único circuito (`fanIdentityService`) que envuelve la llamada Feign a `GET /internal/fans/subscribers/{teamId}`:

| Parámetro | Valor | Por qué |
|---|---|---|
| `slidingWindowSize` | 10 | Cuenta por conteo de llamadas (no por tiempo): mira las últimas 10. |
| `minimumNumberOfCalls` | 5 | Sin este mínimo explícito, Resilience4J espera 100 llamadas (acotadas al tamaño de la ventana) antes de calcular la tasa de fallos. Con 5, la demo no depende de ese detalle implícito de la librería. |
| `failureRateThreshold` | 50 % | Con el mínimo de 5 llamadas, alcanza con 3 fallos para abrir. |
| `waitDurationInOpenState` | 15 s | Cuánto tiempo el circuito rechaza llamadas sin ni siquiera intentar la real. |
| `permittedNumberOfCallsInHalfOpenState` | 3 | Llamadas de prueba que decide si vuelve a `CLOSED` o a `OPEN`. |
| `timeoutDuration` (TimeLimiter) | 3 s | Si fan-identity está lento (no caído), la llamada se corta antes y cuenta como fallo. |

Estados posibles:

- **`CLOSED`**: funcionamiento normal, las llamadas pasan y se cuentan.
- **`OPEN`**: se superó el umbral de fallos; las llamadas se rechazan sin intentar la real (`CallNotPermittedException`) durante `waitDurationInOpenState`.
- **`HALF_OPEN`**: pasado ese tiempo, la primera llamada nueva dispara un lote de prueba (`permittedNumberOfCallsInHalfOpenState`). Si sale bien, vuelve a `CLOSED`; si no, vuelve a `OPEN`.

**El paso de `OPEN` a `HALF_OPEN` no es automático por temporizador**: ocurre recién con la primera llamada después de los 15 s. Si no llega ningún evento nuevo, `GET /api/alerts/status` sigue mostrando `OPEN` aunque ya pasó el tiempo de espera.

## 3. Dónde está en el código

- `config/CircuitBreakerConfiguration.java`: la configuración de arriba, vía `Customizer<Resilience4JCircuitBreakerFactory>` (API programática de Spring Cloud CircuitBreaker; **no** la anotación `@CircuitBreaker`, que requiere otra dependencia — ver gotcha en `CLAUDE.md`).
- `service/FanIdentityLookupService.java`: envuelve la llamada Feign con `circuitBreakerFactory.create("fanIdentityService").run(...)`.
- `service/DispatcherStatusService.java`: lee el estado actual con `CircuitBreakerRegistry.find("fanIdentityService")` (inyectado directo, autoconfigurado por `resilience4j-spring-boot3`).
- `controller/DispatcherStatusController.java`: expone `GET /api/alerts/status` (D-6).

## 4. Qué hace el fallback

Antes de T-31, el fallback devolvía una lista vacía: un evento con fan-identity caído se trataba igual que un evento sin suscriptos, y se perdía en silencio.

Ahora el fallback (`FanIdentityLookupService.fallbackSubscribers`) distingue la causa en el log (circuito abierto vs. fan-identity respondiendo con error) y siempre lanza `SubscribersUnavailableException`. `AlertDispatchService.dispatch` la atrapa y llama a `DispatcherStatusService.recordUndispatched(...)`, que guarda el evento (últimos 20) para que se vea en `GET /api/alerts/status` en vez de perderse.

Esa excepción cubre el fallo de la **consulta** de suscriptores. El **callback** de notificación (`POST /internal/fans/notifications`, uno por fan) es una llamada distinta, fuera del circuito: si falla para un fan puntual no corta el loop de los demás (T-30), pero si falla para **todos** los fans de un evento, `dispatch` también lo registra como no despachado en vez de como "procesado" — si no, el endpoint de estado ocultaría un fallo total de entrega detrás de un `subscriberCount` que no reflejaría a nadie notificado de verdad. `subscriberCount` en `lastProcessedEvents` es entonces "a cuántos fans se notificó con éxito", no "cuántos suscriptos había".

Como ese callback no pasa por el circuito, una degradación de fan-identity limitada a las escrituras (la lectura de suscriptores sigue rápida, así que el circuito permanece `CLOSED`) podría colgar el único hilo del listener de Kafka en el timeout por defecto de Feign (60 s de lectura) por cada fan del loop. Para acotar eso sin agregar un segundo circuito, `application.yaml` de `alert-dispatcher-service` fija un timeout corto específico para este cliente:

```yaml
feign:
  client:
    config:
      fan-identity-service:
        connect-timeout: 1000
        read-timeout: 3000
```

Con esto, un callback que no responde falla en 3 s como mucho en vez de colgarse un minuto; el `try/catch` del loop (T-30) hace el resto.

El reintento automático de esos eventos no despachados **no** está implementado: queda como limitación (sección 6) y como posible tarea nueva.

## 5. Cómo demostrar la apertura del circuito

Requiere el stack completo arriba y `fan1@quienjuega.com` suscripto a `team-colon` (ver `docs/plan_30_consumer_fan_out.md`, sección 4, pasos 1-2).

1. `GET http://localhost:8080/api/alerts/status` → `circuitState: "CLOSED"`.
2. `docker compose stop fan-identity-service`.
3. Ingestar 6 eventos `GOAL` de `team-colon` en `match-001` (mismo curl que en T-21). Cada POST responde `201` igual: match-ingestion no depende de fan-identity.
4. En los logs de `alert-dispatcher-service`: los primeros fallos muestran "Falló la consulta de suscriptores..."; a partir del quinto, "Circuito abierto: no se consultó fan-identity...".
5. `GET /api/alerts/status` → `circuitState: "OPEN"`, con los 6 eventos en `undispatchedEvents` y su motivo.
6. `docker compose start fan-identity-service`, esperar a que aparezca `UP` en Eureka (`:8761`, unos 30-60 s) y a que pasen los 15 s de `waitDurationInOpenState`.
7. Ingestar 3 eventos más: con el primero el circuito pasa a `HALF_OPEN`; si las 3 llamadas de prueba salen bien, vuelve a `CLOSED`.
8. `GET /api/alerts/status` → `circuitState: "CLOSED"` de nuevo, y `fan1` ve esas 3 notificaciones nuevas en `GET /api/fans/me/notifications` (las 6 de mientras el circuito estuvo abierto no se reintentan, ver sección 6).

## 6. Limitaciones aceptadas

- **Sin reintento automático** de los eventos en `undispatchedEvents`: quedan documentados y visibles en el endpoint de estado, pero no se vuelven a despachar solos.
- **El registro de eventos procesados/no despachados vive en memoria** (`DispatcherStatusService`): se pierde al reiniciar el servicio. El log (SLF4J → Loki, T-51) sí queda.
- **`GET /api/alerts/status` todavía no exige rol admin** (R-7): por D-2, `alert-dispatcher-service` no tiene Spring Security propio. Se resuelve en T-40.
