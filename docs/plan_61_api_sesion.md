# Plan de implementación — T-61 · Capa de acceso a la API y sesión

Carril C (Frontend), sobre el scaffold de T-60.

## 1. Contexto y objetivos

Un único módulo que hable con `cloud-gateway`, para que ninguna pantalla futura (T-62 a T-65) haga `fetch` por su cuenta ni conozca otro puerto que no sea el del gateway. Cubre el contrato de `docs/explicacion_contratos_api.md` (T-01) en su totalidad: auth, suscripciones, notificaciones, catálogo, partidos/eventos y estado del despachador.

## 2. Requisitos a satisfacer

1. Todas las llamadas pasan por este módulo y por el gateway (`NEXT_PUBLIC_GATEWAY_URL`), nunca directo a un microservicio.
2. El token se adjunta automáticamente a cada request autenticado; un `401` de respuesta limpia la sesión y redirige a `/login`.
3. Rutas privadas inaccesibles sin sesión (y las de admin, sin rol `ADMIN`).

## 3. Componentes a crear

- **`lib/api.js`** — un `apiFetch(path, options)` central:
  - arma la URL con `NEXT_PUBLIC_GATEWAY_URL`, serializa/deserializa JSON.
  - si hay token en sesión, agrega `Authorization: Bearer <token>`.
  - si la respuesta es `401`, limpia la sesión guardada y redirige a `/login` (solo en el browser — `apiFetch` corre en el cliente).
  - si la respuesta no es 2xx, lanza un `ApiError` con `{status, code, message}` leídos del `ErrorResponseDTO` del contrato, para que cada pantalla muestre el mensaje sin parsear nada de nuevo.
  - funciones específicas por endpoint (`register`, `login`, `getSubscriptions`, `subscribe`, `unsubscribe`, `getNotifications`, `getCatalogLeagues`, `getCatalogTeams`, `getMatches`, `getMatchEvents`, `ingestMatchEvent`, `getAlertsStatus`), todas delgadas sobre `apiFetch`.
- **`lib/session.js`** — helpers de `localStorage` (`saveSession`, `loadSession`, `clearSession`), todos con guarda `typeof window === "undefined"` (Next.js renderiza primero en el servidor). Se guarda `{ token, fanId, email, role }`, exactamente lo que devuelve `POST /api/fans/auth/login`.
- **`context/SessionContext.js`** — `SessionProvider` (envuelve `_app.js`) con `{ session, isLoading, login, logout }`. `login(email, password)` llama a `api.login`, guarda la sesión y actualiza el estado; `logout()` limpia todo y redirige a `/login`.
- **`components/RequireAuth.js`** — wrapper para páginas privadas: mientras `isLoading` no muestra nada (evita parpadeo), si no hay sesión redirige a `/login`, si `adminOnly` y el rol no es `ADMIN` redirige a `/` (o muestra "no autorizado").
- No se toca nada de T-60.

## 4. Plan de verificación

1. `npm run dev`, desde la consola del browser: `login("fan1@quienjuega.com", "fan123")` a mano contra una página de prueba (o ya directamente probado por T-62 una vez existan las pantallas) — confirmar que guarda la sesión y que una llamada subsiguiente (`getSubscriptions`) manda el `Authorization` header.
2. Provocar un `401` (token vencido/adulterado a mano en `localStorage`) y confirmar que `apiFetch` limpia la sesión y redirige a `/login`.
3. Como esta tarea no tiene pantallas propias, la verificación funcional completa queda atada a T-62 (login real) y T-63 (primera pantalla protegida).
