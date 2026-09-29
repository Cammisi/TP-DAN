# Plan de implementación — T-41 · Blindar los endpoints internos

Tarea de Fase 2, en `fan-identity-service` y `alert-dispatcher-service`, más `docker-compose.yml`. Depende de T-40, T-13 y T-14. Incluye la corrección de un bug de T-31 (R-10) porque toca la misma configuración de Feign.

## 1. Contexto y Objetivos

`fan-identity-service` tiene dos endpoints internos que usa `alert-dispatcher-service`:

- `GET /internal/fans/subscribers/{teamId}` (T-13).
- `POST /internal/fans/notifications` (T-14, el callback de D-3).

Hoy están en `permitAll()`. Su única protección es que el gateway no los rutea, y desde T-40 además exige token para todo lo que no sea público, así que desde el host ya no se alcanzan. Pero cualquier contenedor de la red Docker puede llamarlos sin credenciales, y el callback **escribe** en la base: con acceso a la red, cualquiera puede inyectarle notificaciones a cualquier fan.

Esta tarea agrega un secreto compartido entre los dos servicios. `alert-dispatcher-service` lo manda en un header en cada llamada interna, y `fan-identity-service` rechaza con `401` las que no lo traen.

### Corrección incluida: R-10 (timeout de Feign ignorado)

En T-31 se agregó un timeout corto para el cliente Feign hacia fan-identity:

```yaml
feign.client.config.fan-identity-service.read-timeout: 3000
```

Ese prefijo no existe en esta versión. Se verificó en `spring-cloud-openfeign-core-5.0.2.jar`: `FeignClientProperties` usa `@ConfigurationProperties("spring.cloud.openfeign.client")`. Desde OpenFeign 4 las properties pasaron de `feign.*` a `spring.cloud.openfeign.*`. Hoy el timeout **se ignora en silencio**, y el callback usa el default de Feign (60 s de lectura).

La verificación de T-31 sigue siendo válida (no ejercitaba ese escenario), pero la mitigación documentada en `explicacion_circuit_breaker.md` no está activa. Como esta tarea configura ese mismo cliente, se corrige acá.

## 2. Requisitos a satisfacer

1. Ningún endpoint interno responde desde `localhost:8080`.
2. La llamada interna entre servicios se autentica con un secreto compartido por variable de entorno.
3. El secreto no queda hardcodeado en el código.
4. (R-10) El timeout del cliente Feign queda efectivamente aplicado.

### Diseño

- **Header `X-Internal-Api-Key`**, con el mismo valor en los dos servicios. Es un solo secreto compartido, no uno por servicio: hay un único consumidor interno.
- **Del lado de fan-identity:** un filtro nuevo, `InternalApiKeyFilter`, con el mismo patrón que `JwtAuthenticationFilter`.
  - El filtro solo **autentica**: si el path empieza con `/internal/` y el header coincide con la clave configurada, registra la request como autenticada con el rol `ROLE_INTERNAL`.
  - La **autorización** queda en `SecurityConfig`: `/internal/**` pasa de `permitAll()` a `hasRole("INTERNAL")`.
  - Así los `401` salen del mismo `authenticationEntryPoint` que ya existe, en el formato del contrato, sin duplicar código.
- **Comparación en tiempo constante:** `MessageDigest.isEqual(...)` en vez de `equals`. `equals` corta en el primer carácter distinto, y eso deja inferir la clave midiendo tiempos de respuesta.
- **`403` en el formato del contrato:** si alguien de la red llama a `/internal/**` con un JWT de fan y sin la clave, queda autenticado como `FAN` y la regla `hasRole("INTERNAL")` lo rechaza con `403`. Hoy fan-identity no tiene `accessDeniedHandler`, así que ese `403` saldría con el body por defecto de Spring. Se agrega uno que responde `FORBIDDEN` con el mismo `ErrorResponseDTO`. Para no duplicar la escritura del JSON, el entry point y el nuevo handler usan un método privado `writeError(...)` de `SecurityConfig`.
- **Del lado de alert-dispatcher:** un `RequestInterceptor` de Feign, en Java, que agrega el header a cada llamada. Se descartó configurarlo por YAML (`default-request-headers`): R-10 muestra que un error en una property de Feign pasa desapercibido, mientras que un error en Java no compila.
- **Del lado del gateway:** no hay cambios. Desde T-40, `/internal/**` desde el host ya devuelve `401`, porque todo lo no público requiere token.

### Decisiones a aprobar

1. **El secreto va al `.env` que introduce T-40**, como `INTERNAL_API_KEY`, con un valor de ejemplo en `.env.example`. En los `application.yaml` queda un default solo para desarrollo local (`internal.api-key`), igual que `jwt.secret`. Así se cumple la convención de `CLAUDE.md`. Si T-40 se aprobó sin `.env`, esta tarea lo introduce igual, porque este sí es un secreto nuevo.
2. **Escribir `docs/explicacion_seguridad.md` al cerrar esta tarea**, con la historia completa de seguridad (T-11 + T-40 + T-41) para el coloquio. Adelanta parte de T-72.

> **Nota post-implementación (revisión de código):** el `RequestInterceptor` quedó en `client/FanIdentityClientConfiguration` (sin `@Configuration`), referenciado desde `@FeignClient(configuration = ...)`, en vez de un `@Configuration` global. Así la clave interna solo viaja en las llamadas a fan-identity. Dos tests nuevos en `AlertDispatcherServiceApplicationTests` lo verifican: el cliente de fan-identity manda el header y otro cliente no.

## 3. Componentes a crear o modificar

**`fan-identity-service`:**

- **`security/InternalApiKeyFilter.java`** (nuevo): `@Component`, `OncePerRequestFilter`, igual que `JwtAuthenticationFilter`.
  - Recibe `@Value("${internal.api-key}")` por constructor.
  - Si el path empieza con `/internal/` y el header coincide (comparación en tiempo constante), registra un `UsernamePasswordAuthenticationToken` con principal `"internal-service"` y la authority `ROLE_INTERNAL`.
  - En cualquier otro caso, sigue sin autenticar.
- **`config/SecurityConfig.java`**:
  - `/internal/**` → `hasRole("INTERNAL")`. Se saca el comentario que remitía a T-41.
  - Se registra `InternalApiKeyFilter` después de `JwtAuthenticationFilter`.
  - Nuevo `accessDeniedHandler` (`403 FORBIDDEN`).
  - `writeError(...)` privado, compartido con el entry point existente.
- **`application.yaml`**: `internal.api-key` con un default de desarrollo.

**`alert-dispatcher-service`:**

- **`config/FeignConfiguration.java`** (nuevo): `@Configuration` con un `@Bean RequestInterceptor` que agrega `X-Internal-Api-Key` con el valor de `internal.api-key`. Aplica a todos los clientes Feign del servicio; hoy hay uno solo y todos sus paths son internos.
- **`application.yaml`**:
  - `internal.api-key` con el mismo default de desarrollo que fan-identity.
  - **R-10:** el bloque `feign:` pasa a estar bajo `spring.cloud.openfeign.client.config.fan-identity-service` (`connect-timeout: 1000`, `read-timeout: 3000`).
- **Test (R-10):** en `AlertDispatcherServiceApplicationTests` (ya levanta el contexto sin Kafka), inyectar `FeignClientProperties` y verificar que `getConfig().get("fan-identity-service").getReadTimeout()` es `3000`. Es la única forma de confirmar ejecutando que la property se bindea; el escenario real (escrituras lentas en fan-identity) no se puede reproducir a mano.

**Raíz y documentación:**

- **`docker-compose.yml`**: `INTERNAL_API_KEY: ${INTERNAL_API_KEY:?Falta INTERNAL_API_KEY: copiá .env.example a .env}` en `fan-identity-service` y en `alert-dispatcher-service`.
- **`.env.example`**: se agrega `INTERNAL_API_KEY=<valor de ejemplo>`.
- **`docs/explicacion_docker_compose.md`**: la variable nueva.
- **`docs/explicacion_contratos_api.md`**, sección 6: header obligatorio y errores `401`/`403` de los endpoints internos.
- **`docs/explicacion_circuit_breaker.md`** y **`docs/plan_30_consumer_fan_out.md`**: corregir el snippet y las menciones a `feign.client.config` (R-10).
- **`CLAUDE.md`**:
  - Gotcha nuevo: el prefijo de Feign es `spring.cloud.openfeign.client.*` en OpenFeign 4+, y una property mal escrita no da error.
  - En "Endpoints internos fuera de `/api/**`": se protegen además con `X-Internal-Api-Key`.
- **`docs/planificacion_global.md`**: R-10 resuelto al verificar.
- **`docs/explicacion_seguridad.md`** (si se aprueba la decisión 2).

## 4. Plan de verificación

Las llamadas desde dentro de la red usan un contenedor `curlimages/curl` efímero, igual que en T-14. El nombre exacto de la red se ve con `docker network ls | grep tp-nube-network`.

1. Agregar `INTERNAL_API_KEY` al `.env` local y correr `docker compose up -d --build` (ver R-9).
2. **Sin header, desde la red:** `docker run --rm --network <red> curlimages/curl -si http://fan-identity-service:8081/internal/fans/subscribers/team-colon` → `401` con el `ErrorResponseDTO` del contrato.
3. **Header incorrecto** (`-H "X-Internal-Api-Key: otra"`) → `401`.
4. **Header correcto** → `200` con la lista de suscriptores.
5. **JWT de fan sin la clave** (`-H "Authorization: Bearer $TOKEN_FAN"`) → `403 FORBIDDEN` en el formato del contrato.
6. Repetir los pasos 2 a 4 con `POST /internal/fans/notifications`. Sin clave, la notificación **no** aparece en el historial del fan.
7. **Desde el host:** `curl -i localhost:8080/internal/fans/subscribers/team-colon` y `curl -i -X POST localhost:8080/internal/fans/notifications` → `401` del gateway (T-40).
8. **Flujo real:** con `TOKEN_ADMIN`, ingestar un `GOAL` de `team-colon`; fan1 ve la notificación en `GET /api/fans/me/notifications` y `GET /api/alerts/status` lo muestra en `lastProcessedEvents`. Esto prueba que alert-dispatcher manda el header en las dos llamadas.
9. **Clave desalineada (prueba negativa):**
   1. `INTERNAL_API_KEY=otra docker compose up -d alert-dispatcher-service`.
   2. Ingestar un evento: la consulta de suscriptores da `401` y el evento aparece en `undispatchedEvents` de `GET /api/alerts/status`.
   3. Restaurar con `docker compose up -d alert-dispatcher-service`.
10. **R-10:** `mvn test` en `alert-dispatcher-service` en verde, incluida la aserción del timeout.
11. **Nada hardcodeado en Java:** `grep -rn "X-Internal-Api-Key\|internal.api-key" --include=*.java backend-app` solo muestra el nombre del header y el `@Value`, nunca un valor de clave.
