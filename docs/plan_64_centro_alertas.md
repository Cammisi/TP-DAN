# Plan de implementación — T-64 · Centro de alertas

Carril C (Frontend), sobre T-60/T-61/T-62. No depende de T-63.

## 1. Qué

`/alerts`: historial de notificaciones del fan (`GET /api/fans/me/notifications`), según `docs/assets/mockup_centro_alertas.png` (como lista de página completa, no como dropdown del header: ese patrón de notificación flotante no está pedido por ningún requisito y agrega complejidad de UI sin necesidad).

## 2. Requisitos a satisfacer

1. Notificaciones ordenadas de más nueva a más vieja con su fecha (el orden ya lo garantiza el backend, T-14; acá solo se formatea `createdAt`).
2. Estado vacío claro cuando el fan no tiene alertas todavía.
3. Se puede refrescar para ver alertas nuevas (botón "Actualizar" que vuelve a pedir el listado, sin recargar toda la página).

## 3. Componentes a crear

- `pages/alerts.js` (protegida con `RequireAuth`): carga `getNotifications()` al montar, botón de refresco, formatea `createdAt` con `Intl.DateTimeFormat` (sin agregar ninguna librería de fechas).
- `styles/Alerts.module.css`.

## 4. Plan de verificación

Igual que T-63: compila y sirve HTML vía `npm run dev`; la prueba funcional real (con datos reales) queda para la corrida conjunta contra el stack de Docker una vez estén todas las pantallas.
