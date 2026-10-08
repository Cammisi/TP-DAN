# Seguridad

Explica el requisito obligatorio de seguridad del enunciado, construido en tres tareas: T-11 (login y emisión del JWT), T-40 (validación en el gateway, roles y CORS) y T-41 (protección de los endpoints internos). Es el material para defenderlo en el coloquio.

## 1. Qué se protege y de quién

| Amenaza | Defensa | Dónde |
|---|---|---|
| Alguien sin cuenta usa la API | JWT obligatorio en todo lo que no sea login/registro | `cloud-gateway` |
| Un fan hace cosas de admin (cargar eventos, ver el estado del despachador) | Chequeo del rol `ADMIN` | `cloud-gateway` |
| Un fan lee o modifica datos de otro fan | El fan sale del token, nunca del body ni de la URL | `fan-identity-service` |
| Alguien con acceso a la red Docker inyecta notificaciones | Clave compartida en `X-Internal-Api-Key` | `fan-identity-service` |
| Una página de otro origen usa la API desde el navegador del fan | CORS solo para el origen del frontend | `cloud-gateway` |
| Se filtran contraseñas o secretos | BCrypt para contraseñas; secretos en un `.env` fuera del repo | `fan-identity-service`, compose |

## 2. El JWT (T-11)

`fan-identity-service` emite el token en `POST /api/fans/auth/login`:

- **Claims:**
  - `sub`: el id del fan.
  - `role`: `FAN` o `ADMIN`.
  - `email`.
  - Emisión y expiración: 24 h, `jwt.expiration-ms`.
- **Firma:** HMAC con `JWT_SECRET`, una clave simétrica. `jjwt` elige la variante (HS256/HS384/HS512) según el largo de la clave, que tiene que tener al menos 32 caracteres.
- **Contraseñas:** se guardan con BCrypt; nunca en texto plano ni en logs.

Es un token **stateless**: nadie guarda sesiones. Para validarlo alcanza con tener la misma clave y chequear la firma y la expiración.

## 3. Dónde se valida el token (D-2)

El token se valida **en dos lugares**, cada uno por un motivo distinto:

1. **`cloud-gateway` — `JwtValidationFilter` (T-40).** Es la primera línea: corta con `401` antes de rutear, así el tráfico anónimo nunca llega a los microservicios. Además chequea el rol en las dos rutas de admin. Esto importa porque `match-ingestion-service` y `alert-dispatcher-service` **no tienen Spring Security**: sin el gateway, cualquiera podría llamarlos.
2. **`fan-identity-service` — `JwtAuthenticationFilter` (T-11).** Valida de nuevo porque necesita saber **qué fan** hace la request: sus suscripciones y su historial dependen del `sub` del token. Por eso los endpoints del fan nunca reciben un `fanId` por parámetro.

El gateway reenvía el header `Authorization` intacto; así le llega el token a fan-identity.

**Reglas del gateway** (detalle en `docs/explicacion_contratos_api.md`, sección 7):

- Público: solo `/api/fans/auth/**` (login y registro).
- Solo `ADMIN`: cualquier escritura (método distinto de `GET`) sobre `/api/matches/**` o `/api/catalog/**` (T-76: crear partido y crear equipo "al vuelo"), y todo `/api/alerts/**`. Un fan recibe `403 FORBIDDEN`. La regla es por método y prefijo, no por endpoint puntual: si `match-ingestion-service` o `alert-dispatcher-service` agregan otro endpoint de escritura, ya nace protegido, porque no tienen seguridad propia; `fan-identity-service` sí la tiene (su `SecurityConfig`), pero el gateway corta antes de todos modos.
- Todo lo demás requiere un token válido. "Todo lo demás" incluye `/internal/**` y `/actuator/**` del propio gateway: se niega por defecto y se abre explícitamente, no al revés.

**Detalle de implementación que vale mencionar:** el filtro decide con `request.getServletPath()`, que viene normalizado, y no con `getRequestURI()`. Si no, una ruta como `/api/fans/auth/../../matches` empezaría con el prefijo público y se saltearía el chequeo.

## 4. Endpoints internos (T-41)

`fan-identity-service` expone dos endpoints para `alert-dispatcher-service`: suscriptores por equipo y alta de notificación. Tienen dos protecciones:

1. **Están fuera de `/api/**`.** El gateway no tiene una ruta hacia ellos, y además pide token para todo lo no público. Desde el host no se alcanzan.
2. **Exigen `X-Internal-Api-Key`.** Aunque la llamada venga de dentro de la red Docker, sin la clave compartida (`INTERNAL_API_KEY`) fan-identity responde `401`. Esto importa sobre todo para el alta de notificación, porque **escribe** en la base.

La protección se arma en dos partes, con el mismo patrón que el JWT en fan-identity:

- **Autenticación:** `InternalApiKeyFilter` reconoce la clave y marca la request con el rol `INTERNAL`.
- **Autorización:** `SecurityConfig` exige `hasRole("INTERNAL")` en `/internal/**`.

La clave se compara con `MessageDigest.isEqual` (tiempo constante) y no con `equals`, que corta en el primer carácter distinto y deja inferir la clave midiendo tiempos.

Del otro lado, `alert-dispatcher-service` agrega el header con un `RequestInterceptor` de Feign **atado solo a `FanIdentityClient`** (`@FeignClient(configuration = ...)`). Si fuera global y mañana se agregara un cliente Feign hacia otro servicio o una API externa, la clave interna se le mandaría también. Hay un test que verifica que otro cliente no la recibe.

## 5. CORS (T-40)

El navegador bloquea las respuestas de un origen distinto al de la página, salvo que el servidor lo autorice. El frontend (`localhost:3000`) y el gateway (`localhost:8080`) son orígenes distintos, así que el gateway tiene que autorizarlo. Se configura **solo en el gateway**; si los microservicios también agregaran headers CORS, llegarían duplicados y el navegador rechazaría la respuesta.

Los filtros del gateway corren en este orden: primero el de observación de Actuator (mide el request), después CORS y después JWT. Que la observación vaya primero hace que los `401`/`403` también cuenten en las métricas, que es justo lo que interesa mirar en seguridad.

El `CorsFilter` está **antes** del filtro JWT por dos motivos:

- El **preflight** (`OPTIONS` que manda el navegador antes de un request con `Authorization`) no trae token. Si lo viera el filtro JWT, lo cortaría con `401` y el request real nunca saldría.
- Los headers CORS tienen que estar también en las respuestas de error. Si no, el frontend no puede leer un `401` y en su lugar ve un "error de CORS" genérico.

## 6. Secretos

`JWT_SECRET` e `INTERNAL_API_KEY` viven en un `.env` que git ignora. El repo solo tiene `.env.example`, con valores de ejemplo.

- El compose los referencia como `${VAR:?mensaje}`: si faltan, `docker compose up` falla con un mensaje claro, en vez de arrancar con un secreto vacío.
- Para correr un servicio suelto con Maven, cada `application.yaml` tiene un default que **solo sirve para desarrollo local**.

Detalle en `docs/explicacion_docker_compose.md`.

## 7. Cómo demostrarlo en el coloquio

Con el stack arriba, desde Git Bash (`TOKEN_FAN` y `TOKEN_ADMIN` salen del login de un fan y del admin sembrado):

1. `curl -i localhost:8080/api/matches` → `401`, "Falta el token de autenticación". `match-ingestion-service` no tiene seguridad propia: lo cortó el gateway.
2. Mismo request con un token al que se le cambió un carácter de la firma → `401`, "Token inválido o expirado".
3. `POST /api/matches/match-001/events` con `TOKEN_FAN` → `403`. Con `TOKEN_ADMIN` → `201`.
4. `curl -i localhost:8080/internal/fans/subscribers/1` → `401` desde el host.
5. Desde un contenedor en la red Docker, la misma llamada directa a `fan-identity-service:8081` sin header → `401`. Con la clave correcta → `200`. Ver `docs/plan_41_blindar_endpoints_internos.md`, sección 4.

## 8. Limitaciones aceptadas

- **No se pueden revocar tokens.** Al ser stateless, un token robado sirve hasta que expira (24 h). Revocar exigiría guardar una lista negra y consultarla en cada request.
- **Clave simétrica compartida.** Como el gateway valida con la misma clave que firma fan-identity, técnicamente también podría emitir tokens. Con firma asimétrica (RS256), fan-identity firmaría con una clave privada y el gateway validaría con la pública; no se justificó la complejidad extra para el TP.
- **Una sola clave interna para todo.** Si hubiera más consumidores internos, lo ideal sería una clave por servicio, para saber quién llama y poder revocar a uno solo.
- **El tráfico entre contenedores viaja en HTTP**, sin TLS. Se acepta porque la red Docker es privada de la máquina.
- **Sin límite de intentos en el login**, así que no hay protección contra fuerza bruta.
