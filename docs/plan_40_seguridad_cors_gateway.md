# Plan de implementación — T-40 · Seguridad y CORS en el gateway

Tarea de Fase 2, en `cloud-gateway`, más la configuración del secreto JWT en `docker-compose.yml`. Depende de T-11 (ya hecha).

## 1. Contexto y Objetivos

Hoy `cloud-gateway` rutea todo sin mirar nada: un request sin token llega igual a los microservicios. `fan-identity-service` lo rechaza porque tiene su propio filtro JWT (T-11), pero `match-ingestion-service` y `alert-dispatcher-service` no tienen Spring Security (D-2), así que hoy `GET /api/matches` responde sin token, y cualquier fan autenticado puede ingestar eventos o ver el estado del despachador (R-7).

Esta tarea convierte al gateway en la primera línea de seguridad, como decidió D-2:

1. Valida la firma y la expiración del JWT antes de rutear. Sin token o con token inválido responde `401` sin llegar al microservicio.
2. Deja pasar sin token solo las rutas de auth.
3. Chequea el rol `ADMIN` en las dos rutas que el contrato marca como solo-admin (resuelve R-7).
4. Habilita CORS para el origen del frontend.

El token se sigue propagando tal cual al microservicio de destino (el gateway ya reenvía el header `Authorization`; así se verificaron T-11 a T-15), porque `fan-identity-service` lo necesita para saber qué fan hace la request.

## 2. Requisitos a satisfacer

1. Request sin token o con token inválido/expirado a una ruta protegida devuelve `401` sin llegar al microservicio.
2. Rutas de auth (`/api/fans/auth/**`) accesibles sin token.
3. El gateway propaga el token al microservicio de destino.
4. CORS habilitado para el origen del frontend.
5. (R-7) `POST /api/matches/{matchId}/events` y `GET /api/alerts/**` exigen rol `ADMIN`; con un token de fan devuelven `403`.

### Reglas de acceso del gateway

| Ruta | Acceso |
|---|---|
| `OPTIONS` (preflight de CORS) | lo responde el filtro de CORS, no llega al de JWT |
| `/api/fans/auth/**` | público |
| `POST /api/matches/*/events` | solo `ADMIN` |
| `/api/alerts/**` | solo `ADMIN` |
| cualquier otra ruta | cualquier token válido (`FAN` o `ADMIN`) |

"Cualquier otra ruta" incluye `/internal/**` y `/actuator/**` del gateway: **todo lo que no está explícitamente abierto requiere token**, igual que `anyRequest().authenticated()` en fan-identity. Consecuencia para T-50: cuando los healthchecks pasen a `GET /actuator/health`, esa ruta va a tener que sumarse a las públicas (el healthcheck corre dentro del contenedor y también pasa por este filtro).

### Formato de los errores

Mismo `ErrorResponseDTO` del contrato (sección 1). El mensaje del `401` del gateway es distinto al de fan-identity a propósito, para poder ver en la verificación quién respondió:

| Caso | Status | `error` | `message` |
|---|---|---|---|
| Falta el header `Authorization: Bearer ...` | 401 | `UNAUTHORIZED` | `Falta el token de autenticación` |
| Firma inválida, token malformado o expirado | 401 | `UNAUTHORIZED` | `Token inválido o expirado` |
| Token válido sin rol `ADMIN` en ruta de admin | 403 | `FORBIDDEN` | `Se requiere rol administrador` |

`FORBIDDEN` es un código nuevo: se agrega al contrato.

### Decisiones a aprobar

1. **Filtro propio con `jjwt` en vez de Spring Security Resource Server.** Un único `OncePerRequestFilter` con la misma librería y versión que fan-identity (`jjwt` 0.12.6): leer header → validar → chequear rol → `401`/`403`, todo con `if` explícitos. La alternativa (`spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`) resuelve lo mismo con configuración declarativa, pero agrega más conceptos (resource server, `JwtAuthenticationConverter`, prefijo de authorities) para validar un token que el grupo ya sabe validar a mano.
2. **Nuevas dependencias en el `pom` del gateway:** `jjwt-api`, `jjwt-impl` y `jjwt-jackson` 0.12.6, idénticas a las de fan-identity. Hacen falta para verificar la firma HS256 del token.
3. **El `JWT_SECRET` pasa a un `.env` fuera del repo.** Ahora lo necesitan dos servicios (gateway y fan-identity) con el mismo valor, y T-41 agrega un secreto nuevo, que `CLAUDE.md` prohíbe commitear. Propuesta:
   - `docker-compose.yml` referencia `${JWT_SECRET:?...}` en los dos servicios. Si falta el valor, `docker compose up` falla con un mensaje claro en vez de arrancar con otro secreto.
   - Se commitea un `.env.example` con un valor de ejemplo (de al menos 32 caracteres, lo que exige HS256). El `.env` real ya está en `.gitignore`.
   - Costo: cada integrante tiene que hacer una vez `cp .env.example .env`. Se documenta en el README.
   - Alternativa: repetir el literal actual en el bloque de `cloud-gateway` del compose. Es más simple, pero deja dos copias del mismo secreto en el repo y no sirve para el de T-41.
4. **Origen del frontend por defecto: `http://localhost:3000`** (puerto por defecto de Next.js; el navegador corre en el host tanto en desarrollo como con el compose). Configurable con `cors.allowed-origins` en `application.yaml`.

> **Nota post-implementación (revisión de código):**
> - La regla de admin se amplió de `POST /api/matches/*/events` a **toda escritura (no `GET`) sobre `/api/matches/**`**, y `/api/alerts` se chequea sin exigir barra final. Así, un endpoint de escritura nuevo en `match-ingestion-service` nace protegido, y `.../events/` con barra final no se saltea el chequeo.
> - El claim `role` se lee dentro del `try`: un token bien firmado con `role` de otro tipo da `401`, no `500`.
> - Los filtros quedaron en `HIGHEST_PRECEDENCE + 2` (CORS) y `+ 3` (JWT), después del filtro de observación de Actuator (`+ 1`), para que los `401`/`403` se midan.

## 3. Componentes a crear o modificar

**`cloud-gateway`** (paquete `com.tpnube.cloud_gateway`):

- **`pom.xml`**: `jjwt-api` (compile), `jjwt-impl` y `jjwt-jackson` (runtime), versión 0.12.6.
- **`dto/ErrorResponseDTO.java`**: copia del record del contrato, igual que en los otros servicios.
- **`security/JwtValidationFilter.java`**: `OncePerRequestFilter`, **sin** `@Component` (se registra en la config de abajo para controlar el orden). Recibe por constructor el secreto y el `JsonMapper` de Jackson 3 (el bean que expone Boot 4.1).
  1. Si la ruta es pública → sigue la cadena.
  2. Si no hay `Authorization: Bearer ...` → `401` "Falta el token de autenticación".
  3. Valida con `Jwts.parser().verifyWith(key)` (mismo código que `JwtService` de fan-identity). `JwtException` o `IllegalArgumentException` → `401` "Token inválido o expirado". Se loguea el motivo en `debug`, nunca el token.
  4. Si la ruta es solo-admin y el claim `role` no es `ADMIN` → `403`.
  5. Sigue la cadena. El request pasa intacto, así que el header `Authorization` llega al microservicio.
  - `isPublic(request)` e `isAdminOnly(request)` son métodos privados con `if` explícitos sobre método y path (la tabla de la sección 2), sin matchers genéricos.
  - `writeError(response, status, error, message, path)` escribe el `ErrorResponseDTO` con el `JsonMapper`.
- **`config/GatewaySecurityConfig.java`**: `@Configuration` con dos `FilterRegistrationBean`:
  - `CorsFilter` de Spring con orden `Ordered.HIGHEST_PRECEDENCE`. Orígenes desde `cors.allowed-origins`; métodos `GET`, `POST`, `DELETE`, `OPTIONS` (los del contrato); headers `Authorization` y `Content-Type`.
  - `JwtValidationFilter` con orden `HIGHEST_PRECEDENCE + 1`.

  El orden importa: el `CorsFilter` responde el preflight `OPTIONS` sin seguir la cadena (el navegador no manda el token en el preflight), y además agrega los headers CORS a las respuestas reales **antes** de que el filtro JWT pueda cortar con `401`. Sin eso, el frontend vería un error de CORS en vez de poder leer el `401`.
- **`application.yaml`**:
  ```yaml
  jwt:
    secret: default-dev-secret-key-que-tiene-al-menos-256-bits-para-hs256 # mismo default que fan-identity

  cors:
    allowed-origins: http://localhost:3000
  ```

**Raíz del repo:**

- **`.env.example`** (nuevo): `JWT_SECRET=<valor de ejemplo de al menos 32 caracteres>`.
- **`docker-compose.yml`**: `JWT_SECRET: ${JWT_SECRET:?Falta JWT_SECRET: copiá .env.example a .env}` en `fan-identity-service` (reemplaza el literal) y en `cloud-gateway` (nuevo).
- **`docs/explicacion_docker_compose.md`**: el mecanismo de `.env` y la variable nueva del gateway (obligatorio al tocar el compose).
- **`README.md`**: paso `cp .env.example .env` en "Cómo levantar el proyecto".

**Documentación:**

- **`docs/explicacion_contratos_api.md`**: sección nueva "Autenticación en el gateway" con la tabla de reglas de acceso y los tres errores; nota de R-7 en la sección 3 marcada como resuelta.
- **`docs/planificacion_global.md`**: R-7 resuelto al verificar.
- **`CLAUDE.md`**: en la decisión del JWT, agregar que el gateway además chequea el rol en las dos rutas de admin.

No se toca ningún microservicio: fan-identity sigue validando el token por su cuenta (D-2), y match-ingestion/alert-dispatcher siguen sin Spring Security.

## 4. Plan de verificación

Comandos para Git Bash. `TOKEN_FAN` y `TOKEN_ADMIN` salen del login de `fan1@quienjuega.com`/`fan123` y `admin@quienjuega.com`/`admin123`.

1. `cp .env.example .env` y `docker compose up -d --build` (**con `--build`**, ver R-9).
2. **Bloqueo sin token, sin llegar al microservicio:** `curl -i localhost:8080/api/matches` da `401` con `"message":"Falta el token de autenticación"`. Antes de T-40 esta misma request daba `200`, y `match-ingestion-service` no tiene seguridad propia: si ahora da `401`, lo cortó el gateway.
3. **Token adulterado:** cambiar un carácter de la firma de `TOKEN_FAN` → `401` "Token inválido o expirado".
4. **Rutas públicas:** `POST /api/fans/auth/login` sin token → `200` con token.
5. **Propagación del token:** `GET /api/fans/subscriptions` con `TOKEN_FAN` → `200` con las suscripciones de fan1. fan-identity necesita el token para saber qué fan es; si responde bien, el header llegó.
6. **Rol admin (R-7):**
   - `POST /api/matches/match-001/events` con `TOKEN_FAN` → `403 FORBIDDEN`; con `TOKEN_ADMIN` → `201`.
   - `GET /api/alerts/status` con `TOKEN_FAN` → `403`; con `TOKEN_ADMIN` → `200`.
7. **Rutas no públicas del gateway:** `GET localhost:8080/internal/fans/subscribers/team-colon` y `GET localhost:8080/actuator/gateway/routes` sin token → `401`.
8. **CORS con curl:**
   - Preflight: `curl -i -X OPTIONS localhost:8080/api/fans/auth/login -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: content-type"` → `200` con `Access-Control-Allow-Origin: http://localhost:3000`.
   - Mismo preflight con `Origin: http://otro-origen.com` → `403` y sin ese header.
   - `curl -i localhost:8080/api/matches -H "Origin: http://localhost:3000"` (sin token) → `401` **con** `Access-Control-Allow-Origin`, así el frontend puede leer el error.
9. **Desde el navegador (criterio de *Terminada cuando*):** servir cualquier página en el puerto 3000 (por ejemplo `npx http-server -p 3000` en una carpeta vacía; no se commitea nada), abrir la consola del navegador y ejecutar:
   ```js
   const r = await fetch('http://localhost:8080/api/fans/auth/login', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({email: 'fan1@quienjuega.com', password: 'fan123'})});
   const {token} = await r.json();
   await (await fetch('http://localhost:8080/api/fans/subscriptions', {headers: {Authorization: 'Bearer ' + token}})).json();
   ```
   Tiene que devolver las suscripciones sin errores de CORS. Repetir el segundo `fetch` con el token adulterado → `401` legible desde JavaScript.
10. **Regresión del carril B:** con `TOKEN_ADMIN`, ingestar un `GOAL` de un equipo que fan1 sigue y confirmar con `TOKEN_FAN` que la notificación aparece en `GET /api/fans/me/notifications`.
