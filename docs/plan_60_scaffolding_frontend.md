# Plan de implementación — T-60 · Scaffolding de `frontend-app`

Primera tarea del carril C (Frontend). Sin plan_* previo porque no depende de decisiones abiertas: Next.js Pages Router ya está decidido en `CLAUDE.md`.

## 1. Contexto y objetivos

Crear el proyecto Next.js que va a consumir exclusivamente `cloud-gateway` (`:8080`), con su Dockerfile y su servicio en `docker-compose.yml`. Es solo scaffolding: la capa de acceso a la API es T-61, las pantallas son T-62 a T-65.

## 2. Decisiones tomadas (sin alcance abierto del enunciado)

- **JavaScript, no TypeScript**: mantiene la barrera de entrada baja para defender en el coloquio (criterio "simple antes que genérico" de `CLAUDE.md`).
- **CSS Modules (incluido en Next.js), no Tailwind ni ninguna librería de UI**: cero dependencias nuevas que justificar. Los mockups de `docs/assets/` se toman como referencia de layout, no como pixel-perfect.
- **Puerto 3000**: libre en el host (Grafana ya usa 3001, ver `docker-compose.yml`).
- Se generó con `create-next-app` y se eliminaron `CLAUDE.md`/`AGENTS.md`/`pages/api/` que el scaffold agrega por defecto: el repo ya tiene su propio `CLAUDE.md` raíz y no hace falta ninguna API route de Next (todo pasa por el gateway).

## 3. Componentes a crear

- `frontend-app/` completo vía `create-next-app --js --eslint --no-tailwind --no-src-dir --no-app`.
- `frontend-app/.env.local.example`: documenta `NEXT_PUBLIC_GATEWAY_URL` (default `http://localhost:8080` para desarrollo local fuera de Docker).
- `frontend-app/pages/index.js`: página inicial mínima que confirma que el contenedor responde y muestra qué gateway URL tiene configurada (sin llamarlo todavía, eso es T-61).
- `frontend-app/Dockerfile`: build multi-stage (mismo patrón que los servicios Java: build con `node`, runtime liviano con `node:*-alpine` y `next start`).
- `docker-compose.yml`: nuevo servicio `frontend-app`, puerto `3000:3000`, `NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080` (es una variable de **build-time** para Next.js que termina en el bundle del browser, así que tiene que apuntar a una URL alcanzable desde el navegador del usuario, no desde la red Docker — de ahí `localhost:8080`, no `http://cloud-gateway:8080`).
- `docs/explicacion_docker_compose.md`: se actualiza (regla de `CLAUDE.md`: no tocar `docker-compose.yml` sin actualizar ese doc).

## 4. Plan de verificación

1. `npm run dev` local (Node está disponible en esta máquina) → `http://localhost:3000` muestra la página inicial.
2. `docker compose up -d --build frontend-app` → el contenedor queda `healthy`/responde y `http://localhost:3000` sirve la misma página vía Docker.
3. Confirmar que ninguna otra tarea del compose se rompió (el resto de los healthchecks siguen en verde).
