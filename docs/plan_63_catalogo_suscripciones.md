# Plan de implementación — T-63 · Catálogo y mis suscripciones

Carril C (Frontend), sobre T-60/T-61/T-62.

## 1. Qué

Una sola pantalla protegida (`/subscriptions`) con dos partes: buscador de equipos (catálogo) y el listado de "mis suscripciones" con toggle para seguir/dejar de seguir, inspirada en `docs/assets/mockup_mis_suscripciones.png` (sin las secciones "Resultados"/"Favoritos" del mockup: no están en el contrato ni en ninguna tarea).

## 2. Nota de alcance — "Panel Principal" no es T-63

El mockup `mockup_panel_principal.png` (muro de partidos en vivo con marcador) **no está cubierto por ninguna tarea** de `docs/planificacion_global.md`. T-63 es específicamente catálogo + suscripciones. Dejo `/` (home autenticada) como un resumen simple (cantidad de suscripciones + accesos directos), no como el muro de partidos del mockup — eso requeriría una tarea nueva que combine `GET /api/matches` (T-22) con el catálogo, y no la voy a inventar. Lo aviso también en el resumen al usuario.

## 3. Requisitos a satisfacer

1. Búsqueda de equipos por nombre (`GET /api/catalog/teams?name=`).
2. Seguir/dejar de seguir refleja el cambio sin recargar la página (estado de React, no `router.reload()`).
3. El listado de suscripciones muestra nombres de equipo y liga, no ids (ya vienen resueltos por el backend, T-12).

## 4. Componentes a crear

- `pages/subscriptions.js` (protegida con `RequireAuth`): carga `getSubscriptions()` y `getCatalogTeams()` al montar; buscador con `onChange` debounced simple (sin dependencias nuevas, `setTimeout`/`clearTimeout` a mano); cada equipo del catálogo muestra un toggle — si `teamId` está en el set de suscripciones actual, "Dejar de seguir" (`unsubscribe`), si no, "Seguir" (`subscribe`); tras cada acción actualiza el estado local (no vuelve a pedir todo el catálogo).
- `pages/index.js` se reemplaza por el resumen protegido descrito arriba (usa `AppLayout`).
- `styles/Subscriptions.module.css`.

## 5. Plan de verificación

1. `npm run dev`, confirmar que `/subscriptions` compila y (sin browser interactivo disponible) el HTML servido contiene el buscador y las secciones esperadas.
2. Verificación funcional real contra el backend: una vez todas las pantallas estén armadas, probar el flujo con el stack de Docker completo (login → buscar equipo → seguir → ver en "mis suscripciones").
