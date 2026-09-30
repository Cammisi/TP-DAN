# Plan de implementación — T-65 · Panel de administrador

Carril C (Frontend), sobre T-63 (necesita `AppLayout`/`RequireAuth`), T-21 (ingesta) y T-31 (estado del circuito).

## 1. Qué

`/admin`, accesible solo con rol `ADMIN` (`RequireAuth adminOnly`): formulario para simular un evento deportivo (`POST /api/matches/{matchId}/events`) + estado del despachador (`GET /api/alerts/status`) + enlaces a Eureka/Kafka UI/Grafana.

## 2. Requisitos a satisfacer

1. Solo accesible con rol admin (`RequireAuth` ya resuelve esto, reusado de T-61/T-63).
2. Formulario de simulación de evento que llama al endpoint de ingesta.
3. Estado del despachador y enlaces a Eureka, Kafka UI y Grafana.

## 3. Componentes a crear

- `pages/admin.js`:
  - Selector de partido (`getMatches()` para poblar un `<select>`; al elegir uno se derivan `homeTeamId`/`awayTeamId` para el selector de equipo).
  - Selector de tipo de evento (`GOAL`/`CARD`/`MATCH_END`) que muestra solo los campos que aplican, según el `MatchEventDTO` del contrato (T-01, sección 3.1): `GOAL`/`CARD` piden `teamId`+`playerName`; `CARD` además `cardType`; `GOAL`/`MATCH_END` piden `homeScore`/`awayScore`; `MATCH_END` no pide `teamId` (event de ambos equipos).
  - `minute` y `description` siempre; `occurredAt` se manda como `new Date().toISOString()` en el submit (no hace falta pedirlo en el form).
  - Al enviar, llama a `ingestMatchEvent(matchId, dto)`; muestra el `eventId` devuelto o el error.
  - Sección de estado: `getAlertsStatus()` con botón de refresco (mismo patrón que T-64), muestra `circuitState`, `lastProcessedEvents` y `undispatchedEvents`.
  - Enlaces fijos: Eureka `http://localhost:8761`, Kafka UI `http://localhost:8090`, Grafana `http://localhost:3001` (puertos ya fijados en `docker-compose.yml`, no hace falta leerlos de ninguna API).
- `styles/Admin.module.css`.

## 4. Plan de verificación

1. Compila y sirve vía `npm run dev`.
2. Contra el stack de Docker: loguear como `admin@quienjuega.com`, simular un evento de un equipo que un fan de prueba sigue, y confirmar que aparece en `GET /api/alerts/status` y en el centro de alertas de ese fan (cierra el mismo flujo de punta a punta que T-42, ahora disparado desde la UI).
