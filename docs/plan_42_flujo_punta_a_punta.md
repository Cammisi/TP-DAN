# Plan de implementación — T-42 · Verificación del flujo de punta a punta

Tarea de Fase 2, transversal. Depende de T-30, T-40 y T-41. No agrega código: ejercita el sistema completo y corrige (o anota como tarea nueva) lo que aparezca.

## 1. Contexto y Objetivos

Cada tarea anterior se verificó por separado. Esta tarea recorre el camino completo de un fan, pasando **siempre por el gateway** y con la seguridad de T-40 y T-41 activa:

```
registro → login → buscar equipo → suscribirse → (admin) ingestar evento → ver la notificación
```

Es exactamente la demo del coloquio, así que tiene que funcionar antes de construir el frontend encima. Además, el recorrido queda escrito de forma reproducible (sección 4), y T-71 lo va a convertir en una colección `.http`.

## 2. Requisitos a satisfacer

1. El recorrido completo funciona pasando siempre por el gateway (`localhost:8080`), nunca directo a un microservicio.
2. Quedan registrados los pasos exactos para reproducirlo: los de la sección 4 de este plan, con las respuestas esperadas.
3. Los errores encontrados se corrigen o se anotan como tareas nuevas en `docs/planificacion_global.md`.
4. Se reproduce **desde cero**: `docker compose down -v && docker compose up --build`. El `-v` borra los volúmenes, así que se prueba también que las semillas, el esquema de Postgres y el tópico de Kafka se recrean solos.

### Criterio para lo que aparezca

- **Bug de algo ya implementado:** se corrige dentro de esta tarea y se anota en la nota de cierre, con qué tarea lo había introducido.
- **Falta algo que ninguna tarea pedía:** no se implementa; se agrega como tarea nueva a la planificación, según la regla de `CLAUDE.md`.

## 3. Componentes

No se crea código. Al cerrar la tarea se toca:

- **`docs/planificacion_global.md`**: nota de *Terminada cuando* con fecha, lo encontrado, y las tareas nuevas que surjan.
- **`README.md`**: si el recorrido obliga a cambiar algún paso de "Cómo levantar el proyecto".

## 4. Recorrido

Comandos para Git Bash, desde la raíz del repo.

- **Precondición:** `.env` creado a partir de `.env.example` (T-40, T-41).
- **Extraer el token del login sin `jq`:** `sed -E 's/.*"token":"([^"]+)".*/\1/'`.

### 4.1 Levantar de cero

```bash
docker compose down -v
docker compose up --build -d
```

Esperar a que los 9 contenedores estén `healthy` (`docker compose ps`) y a que los 4 clientes figuren `UP` en Eureka (`http://localhost:8761`).

### 4.2 Recorrido del fan

| # | Paso | Request | Esperado |
|---|---|---|---|
| 1 | Registro | `POST /api/fans/auth/register` `{"email":"demo@quienjuega.com","password":"demo1234"}` | `201` con `id` |
| 2 | Login | `POST /api/fans/auth/login` (mismo body) → guardar `TOKEN_FAN` | `200`, `role: "FAN"` |
| 3 | Buscar equipo | `GET /api/catalog/teams?name=colon` con `TOKEN_FAN` | `200`, incluye `"id":"team-colon"` |
| 4 | Suscribirse | `POST /api/fans/subscriptions` `{"teamId":"team-colon"}` con `TOKEN_FAN` | `201` con `teamName` y `leagueName` |
| 5 | Ver suscripciones | `GET /api/fans/subscriptions` con `TOKEN_FAN` | `200`, aparece Colón |
| 6 | Historial vacío | `GET /api/fans/me/notifications` con `TOKEN_FAN` | `200`, `[]` |
| 7 | Login admin | `POST /api/fans/auth/login` `{"email":"admin@quienjuega.com","password":"admin123"}` → guardar `TOKEN_ADMIN` | `200`, `role: "ADMIN"` |
| 8 | Ingestar evento | `POST /api/matches/match-001/events` `{"teamId":"team-colon","type":"GOAL","minute":78,"playerName":"Facundo Farías","homeScore":1,"awayScore":0}` con `TOKEN_ADMIN` → guardar `EVENT_ID` | `201` con `eventId` |
| 9 | Ver la alerta | `GET /api/fans/me/notifications` con `TOKEN_FAN` (unos segundos después) | `200`, una notificación con `eventId == EVENT_ID` y texto `"¡Gol! Facundo Farías (78') — marcador 1-0"` |
| 10 | Estado del despachador | `GET /api/alerts/status` con `TOKEN_ADMIN` | `circuitState: "CLOSED"`, `EVENT_ID` en `lastProcessedEvents` con `subscriberCount: 1` |
| 11 | Partido actualizado | `GET /api/matches?teamId=team-colon` con `TOKEN_FAN` | `match-001` en `LIVE` con marcador 1-0 |

### 4.3 Chequeos de seguridad sobre el mismo stack

| # | Caso | Esperado |
|---|---|---|
| 12 | Paso 5 sin token | `401` del gateway |
| 13 | Paso 5 con `TOKEN_FAN` adulterado | `401` |
| 14 | Paso 8 con `TOKEN_FAN` | `403 FORBIDDEN` |
| 15 | `GET localhost:8080/internal/fans/subscribers/team-colon` | `401` |
| 16 | Paso 1 repetido (email duplicado) | `409 EMAIL_ALREADY_EXISTS` |

### 4.4 Persistencia tras reiniciar

```bash
docker compose restart
```

Repetir los pasos 5 y 9 con los mismos tokens: la suscripción y la notificación siguen ahí.

## 5. Plan de verificación

La tarea queda terminada cuando todos los pasos de la sección 4 dan lo esperado sobre un stack levantado con `docker compose down -v && docker compose up --build`, en una sola corrida y sin tocar nada a mano entre pasos. Si algún paso falla:

1. Se corrige según el criterio de la sección 2.
2. Se vuelve a correr **todo** el recorrido desde 4.1, no solo el paso que falló.
