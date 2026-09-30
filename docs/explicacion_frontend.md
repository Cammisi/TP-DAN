# Frontend

Explica cómo está armado `frontend-app/`, construido en seis tareas: T-60 (scaffolding), T-61 (API y sesión), T-62 (login/registro), T-63 (catálogo y suscripciones), T-64 (centro de alertas) y T-65 (panel de administrador). Es el material para defenderlo en el coloquio.

## 1. Decisiones

- **Next.js, Pages Router, JavaScript plano.** Sin TypeScript: mantiene la barrera de entrada baja para defender en el coloquio (criterio "simple antes que genérico" de `CLAUDE.md`). Pages Router (no App Router) porque el ruteo por archivos alcanza para separar pantallas por rol sin necesitar layouts anidados ni Server Components.
- **CSS Modules, sin librería de UI.** Vienen incluidos en Next.js: cero dependencias nuevas que justificar. Los mockups de `docs/assets/` son referencia de layout, no pixel-perfect.
- **`localStorage` + React Context para la sesión, sin librería de estado.** El JWT y los datos de sesión (`fanId`, `role`, `email`) se guardan en un único objeto JSON bajo la clave `qj_session`. No hay Redux/Zustand/React Query: el volumen de estado del TP no lo justifica.
- **Salida `standalone`** (`next.config.mjs`, `output: "standalone"`): la imagen de Docker copia solo lo que hace falta para `node server.js`, sin el `node_modules` completo.

## 2. Estructura

```
frontend-app/
├── pages/
│   ├── index.js            # Home autenticada: resumen (T-63, ver nota de alcance abajo)
│   ├── login.js             # T-62
│   ├── register.js          # T-62
│   ├── subscriptions.js      # T-63: catálogo + mis suscripciones
│   ├── alerts.js            # T-64: historial de notificaciones
│   └── admin.js             # T-65: simular evento + estado del despachador
├── lib/
│   ├── api.js               # Único punto de acceso al gateway (T-61)
│   └── session.js           # Helpers de localStorage
├── context/
│   └── SessionContext.js    # Sesión como React Context (T-61)
├── components/
│   ├── RequireAuth.js       # Wrapper de rutas privadas (T-61)
│   └── AppLayout.js         # Sidebar + contenido, compartido por las pantallas protegidas
└── styles/                  # Un módulo CSS por pantalla/componente
```

## 3. La capa de API (T-61)

`lib/api.js` es el **único** lugar del proyecto que llama `fetch` — ninguna pantalla habla con el gateway por su cuenta. Un `apiFetch(path, options)` central:

- arma la URL con `NEXT_PUBLIC_GATEWAY_URL`;
- adjunta `Authorization: Bearer <token>` si hay sesión guardada;
- si la respuesta es `401` **y la request llevaba un token nuestro** (para no confundir con un `401` de credenciales inválidas en el login), limpia la sesión y redirige a `/login`;
- si la respuesta no es 2xx, lanza un `ApiError` con `{status, code, message}` leídos del `ErrorResponseDTO` del contrato (T-01), para que cada pantalla muestre `error.message` directo, sin mapear códigos a mano.

Cada endpoint del contrato tiene su función delgada arriba de `apiFetch` (`login`, `register`, `getSubscriptions`, `subscribe`, `getCatalogTeams`, `getMatches`, `ingestMatchEvent`, `getAlertsStatus`, etc.).

### 3.1 La sesión no incluye email del backend

**Detalle no obvio, encontrado probando en el browser real (no lo detectaba `curl`):** `POST /api/fans/auth/login` no devuelve `email` en el contrato, solo `token`/`fanId`/`role`. `SessionContext.login` arma el objeto de sesión agregando el `email` que el usuario ya tipeó en el formulario:

```js
const result = await api.login(email, password);
const session = { ...result, email }; // email no viene del backend
```

Si se agrega un nuevo campo a la UI que dependa de un dato del fan que el login no devuelve, hay que resolverlo de la misma manera (o pedirlo aparte) — no asumir que la respuesta del login trae todo lo que la UI necesita mostrar.

## 4. Rutas privadas y rol admin (T-61/T-65)

`components/RequireAuth.js` envuelve cada pantalla protegida:

```jsx
<RequireAuth adminOnly>
  <AdminContent />
</RequireAuth>
```

Mientras la sesión todavía no se leyó de `localStorage` (`isLoading`) no renderiza nada, para no mostrar un parpadeo de contenido protegido antes de saber si hay sesión. Sin sesión, redirige a `/login`. Con `adminOnly` y rol distinto de `ADMIN`, redirige a `/`.

Esto es una redirección **de cliente**: no reemplaza la seguridad real, que vive en el gateway (T-40) y en cada microservicio. Si alguien desactiva JavaScript o llama al gateway directo, `RequireAuth` no lo frena — pero el gateway sí (`403` con un token de fan sobre una ruta de admin).

## 5. Nota de alcance — no todo lo de los mockups está implementado

`docs/assets/mockup_panel_principal.png` muestra un "muro de partidos en vivo" (marcador + eventos recientes). Esa pantalla **no está cubierta por ninguna tarea** de `docs/planificacion_global.md` (T-63 es específicamente catálogo + suscripciones). Por la regla de `CLAUDE.md` de no inventar alcance, `/` quedó como un resumen simple (cantidad de equipos seguidos / alertas recibidas, con accesos directos), y el muro de partidos se dejó anotado como tarea propuesta (**T-66**, sin asignar) en la planificación.

## 6. Cómo correrlo fuera de Docker (desarrollo)

```bash
cd frontend-app
cp .env.local.example .env.local   # NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080
npm install
npm run dev
```

Requiere el resto del stack (al menos `cloud-gateway` y los tres microservicios) levantado vía Docker para tener algo que consumir.

## 7. Gotcha de Docker: el server standalone y `HOSTNAME`

Si se vuelve a tocar el `Dockerfile` del frontend: el server que genera `next build` con `output: "standalone"` usa la variable de entorno `HOSTNAME` para decidir en qué interfaz escuchar. Docker le setea `HOSTNAME` automáticamente al id del contenedor en **todo** contenedor, así que sin `ENV HOSTNAME=0.0.0.0` explícito en el Dockerfile, el server queda escuchando solo en la IP interna de la red Docker: el contenedor arranca, pero ni el healthcheck ni el puerto publicado al host lo alcanzan. Detalle completo en el gotcha de `CLAUDE.md`.
