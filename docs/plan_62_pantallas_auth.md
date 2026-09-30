# Plan de implementación — T-62 · Pantallas de registro y login

Carril C (Frontend), sobre T-60 (scaffold) y T-61 (API + sesión).

## 1. Qué

`/login` y `/register`, layout de tarjeta centrada inspirado en `docs/assets/mockup_login.png` (sin el branding "SportFlash"/login social del mockup: por `CLAUDE.md` el nombre del producto es **¿Quién Juega?**, y login con Google/Apple no está en el contrato de T-01, así que no se agrega).

## 2. Requisitos a satisfacer

1. Registro y login funcionales contra el gateway (usan `lib/api.js` de T-61, nada de `fetch` propio).
2. Errores del backend (`EMAIL_ALREADY_EXISTS`, `INVALID_CREDENTIALS`, `INVALID_REQUEST`) se muestran con el `message` que ya manda el backend (legible, sin mapear códigos a mano).
3. Tras loguear, redirige a `/` (panel principal, placeholder hasta T-63).
4. Después de registrarse, redirige a `/login` con un aviso de éxito (no auto-login: mantiene el flujo simple, un solo camino para entrar con sesión).

## 3. Componentes a crear

- `pages/login.js` + `styles/Auth.module.css` (compartido con register): formulario controlado (`email`, `password`), `useSession().login(...)`, estado de error (`ApiError.message`) y de carga (deshabilita el botón mientras la request está en curso).
- `pages/register.js`: mismo layout, llama a `api.register`, redirige a `/login?registered=1`. `login.js` lee ese query param para mostrar "Cuenta creada, iniciá sesión".
- `pages/index.js` se ajusta mínimamente: si hay sesión, saluda por email y ofrece "Cerrar sesión"; si no, linkea a `/login`. (El panel principal real es T-63/T-64; acá solo evita que la home quede huérfana de la sesión que ya existe desde T-61.)

## 4. Plan de verificación

1. `npm run dev` (Node local) + `curl` contra `/login` y `/register`: confirmar 200 y que el HTML contiene el formulario.
2. Sin browser disponible en este entorno para probar la interacción real (submit, redirect, localStorage): se deja explícitamente pendiente de una prueba manual del usuario, o de T-63 cuando haya una pantalla protegida real que ejercite el flujo completo.
3. Una vez el resto de las pantallas (T-63) exista, probar el flujo E2E real: registrarse → loguear → ver el panel.
