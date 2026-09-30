# ¿Quién Juega?

Plataforma de seguimiento deportivo en tiempo real. Centraliza eventos de distintos proveedores deportivos y permite a los usuarios suscribirse a los equipos/ligas que les interesan para recibir alertas (goles, fin de partido, tarjetas, etc.) sin tener que monitorear activamente múltiples fuentes.

Trabajo práctico integrador de **Desarrollo de aplicaciones en la Nube** — UTN Santa Fe, 2026.

**Alumnos:** Bautista Lazzarini, Bautista Luetich, Jose Cammisi.

## El problema

Los fanáticos del deporte consumen información desde redes sociales, sitios de noticias y apps oficiales, pero la dispersión de fuentes dificulta el seguimiento personalizado en tiempo real. **¿Quién Juega?** resuelve esto centralizando los eventos y despachando notificaciones internas según las suscripciones de cada usuario.

**Alcance del TP:** registro/autenticación de usuarios, suscripción a equipos/ligas y despacho de notificaciones internas en la plataforma. Queda fuera de alcance la integración con sistemas externos que no sean proveedores de datos deportivos (no hay envío de email/push a terceros).

## Arquitectura

Arquitectura de microservicios con descubrimiento de servicios (Eureka) y un API Gateway como único punto de entrada para el frontend.

```
frontend-app ──▶ cloud-gateway (8080) ──▶ ┌ fan-identity-service (PostgreSQL)
                                          ├ match-ingestion-service (MongoDB)
                                          └ alert-dispatcher-service
                        │
                    eureka-server (registro y descubrimiento)

match-ingestion-service ──▶ Kafka ──▶ alert-dispatcher-service ──▶ fan-identity-service
                                       (protegido con Circuit Breaker)
```

| Servicio | Responsabilidad | Datos |
|---|---|---|
| **fan-identity-service** | Registro, login, JWT, perfiles, catálogo de ligas y equipos, y suscripciones (fan ↔ equipo) | PostgreSQL |
| **match-ingestion-service** | Ingesta y valida eventos de partidos desde proveedores externos, los persiste y los publica en Kafka | MongoDB |
| **alert-dispatcher-service** | Consume eventos de Kafka, consulta a fan-identity-service quiénes están suscritos y genera las notificaciones | Sin base propia (Single Source of Truth) |
| **eureka-server** | Service discovery | — |
| **cloud-gateway** | Punto de entrada único, enruta por load balancing hacia los microservicios vía Eureka | — |

La llamada de `alert-dispatcher-service` a `fan-identity-service` está protegida con **Circuit Breaker** (Resilience4j): si fan-identity-service cae o responde con latencia alta, el circuito se abre para evitar que alert-dispatcher-service colapse esperando respuestas.

El catálogo de ligas y equipos vive en `fan-identity-service` y no en `match-ingestion-service`, aunque este último sea el dueño del dominio deportivo: así la suscripción tiene integridad referencial real contra el equipo, que es la razón por la que se eligió PostgreSQL. `match-ingestion-service` guarda partidos y eventos referenciando el identificador del equipo, sin clave foránea entre servicios.

## Roles de usuario

- **Fan:** se registra/inicia sesión, busca equipos y ligas, gestiona sus suscripciones (seguir/dejar de seguir) y consulta su historial de alertas.
- **Administrador:** monitorea el estado de los microservicios y puede simular la carga de un evento deportivo para disparar el flujo completo de punta a punta.

## Tecnologías

- **Backend:** Spring Boot 4.1, Spring Cloud 2025.1.2 (Eureka, Gateway MVC, OpenFeign, Circuit Breaker/Resilience4j), Spring Security + JWT, Java 25.
- **Mensajería:** Apache Kafka (modo KRaft, sin ZooKeeper).
- **Persistencia:** PostgreSQL (fan-identity-service), MongoDB (match-ingestion-service).
- **Frontend:** Next.js (Pages Router).
- **Infraestructura:** Docker / Docker Compose.

## Estado del proyecto

Backend y frontend están completos y verificados en ejecución: infraestructura (Eureka, gateway, Docker Compose con Postgres, MongoDB, Kafka y el stack de observabilidad), los tres microservicios de negocio, seguridad de punta a punta (JWT, rol admin, CORS, endpoints internos protegidos), métricas y logs centralizados, y el frontend en Next.js (login/registro, catálogo y suscripciones, centro de alertas, panel de administrador). Tests automatizados y la colección de requests reproducible también están hechos. Falta el ensayo final del coloquio (T-73).

El detalle del avance, las tareas pendientes y el orden en que hay que abordarlas están en **[docs/planificacion_global.md](docs/planificacion_global.md)**.

## Estructura del repositorio

```
tp-deportes-nube/
├── backend-app/
│   ├── infrastructure/
│   │   ├── eureka-server/              # Service Discovery — puerto 8761
│   │   └── cloud-gateway/              # API Gateway — puerto 8080
│   └── microservices/
│       ├── fan-identity-service/       # puerto 8081 — PostgreSQL
│       ├── match-ingestion-service/    # puerto 8082 — MongoDB + Kafka
│       └── alert-dispatcher-service/   # puerto 8083 — Kafka
├── frontend-app/                       # puerto 3000 — Next.js, consume únicamente el gateway
├── infrastructure/                     # Config de Prometheus, Grafana, Loki y Promtail (T-50/T-51)
├── docs/                               # Planificación y documentación del TP
│   └── quien-juega.http                # Colección de requests reproducible (T-71)
├── docker-compose.yml
├── .env.example                        # Plantilla de secretos — copiar a .env antes de levantar
├── README.md
└── CLAUDE.md                           # Instrucciones para la IA que trabaja sobre el proyecto
```

## Cómo levantar el proyecto

Requisitos: Docker y Docker Compose.

```bash
# Una sola vez, antes del primer arranque: crear el archivo de secretos local (git lo ignora)
cp .env.example .env

# Compilar todas las imágenes y levantar el stack completo
docker compose up --build

# Levantar en segundo plano
docker compose up --build -d

# Reconstruir un único servicio
docker compose up --build fan-identity-service

# Ver logs de un servicio
docker compose logs -f cloud-gateway

# Apagar (conserva los volúmenes de datos)
docker compose down

# Apagar y borrar también los volúmenes (reset completo de datos)
docker compose down -v
```

El orden de arranque está gobernado por `depends_on` con `condition: service_healthy`: las bases de datos y Kafka deben pasar su healthcheck antes de que arranquen los microservicios correspondientes, y `eureka-server` debe estar saludable antes que cualquier cliente Eureka.

> El levantamiento de los 13 contenedores y el recorrido funcional de punta a punta (registro → login → catálogo → suscripción → evento → alerta, con la seguridad de T-40/T-41 activa) están verificados de punta a punta — ver **Cómo probar** abajo.

## Cómo probar

Sin escribir código, dos formas:

1. **Desde el navegador**, en `http://localhost:3000` (ver **Puertos y paneles**). Usuarios semilla (`fan-identity-service` los crea al arrancar, siempre los mismos):

   | Rol | Email | Contraseña |
   |---|---|---|
   | Fan | `fan1@quienjuega.com` | `fan123` |
   | Admin | `admin@quienjuega.com` | `admin123` |

   O registrar una cuenta nueva desde `/register` (queda con rol `FAN`).

2. **Sin frontend**, con la colección reproducible: [docs/quien-juega.http](docs/quien-juega.http) — requiere la extensión **REST Client** de VS Code. Cubre el recorrido completo (registro, login, catálogo, suscripción, ingesta de un evento por el admin, la alerta llegándole al fan) más los chequeos de seguridad de T-40/T-41, de arriba a abajo sobre un stack recién levantado.

## Puertos y paneles

| Servicio | Puerto | Expuesto al host | Descripción |
|---|:---:|:---:|---|
| frontend-app | 3000 | Sí | La aplicación web |
| cloud-gateway | 8080 | Sí | Único puerto que debe consumir el frontend |
| eureka-server | 8761 | Sí | Dashboard de registro de servicios |
| fan-identity-service | 8081 | No | Solo accesible dentro de la red Docker / vía gateway |
| match-ingestion-service | 8082 | No | Solo accesible dentro de la red Docker / vía gateway |
| alert-dispatcher-service | 8083 | No | Solo accesible dentro de la red Docker / vía gateway |
| PostgreSQL | 5432 | Sí | Base de `fan-identity-service` (DB: `fan_identity_db`) |
| MongoDB | 27017 | Sí | Base de `match-ingestion-service` (DB: `match_ingestion_db`) |
| Kafka | 9092 | Sí | Broker en modo KRaft. Listener interno para contenedores: `kafka:19092` |
| Kafka UI | 8090 | Sí | Panel web de administración de tópicos y consumers |
| Prometheus | 9090 | Sí | Métricas scrapeadas de los 5 servicios Spring (T-50) |
| Grafana | 3001 | Sí | Dashboard de métricas y logs (usuario/clave por defecto: `admin`/`admin`) |
| Loki | 3100 | Sí | API de almacenamiento de logs (T-51), se consulta desde Grafana |

Todos los servicios corren en la red Docker `tp-nube-network` y se resuelven entre sí por nombre de contenedor.

> Las credenciales de PostgreSQL están hardcodeadas en `docker-compose.yml` para simplificar el entorno local del TP. No usar esos valores fuera de este contexto académico.

## Documentación

Toda la documentación vive en [docs/](docs/):

| Archivo | Contenido |
|---|---|
| [planificacion_global.md](docs/planificacion_global.md) | Planificación general y seguimiento del avance. **Punto de partida para trabajar.** |
| [enunciado.md](docs/enunciado.md) | Enunciado original de la cátedra |
| [resolucion_propuesta.md](docs/resolucion_propuesta.md) | Informe con la solución propuesta por el grupo, incluidos los mockups |
| [explicacion_contratos_api.md](docs/explicacion_contratos_api.md) | Contrato de todas las APIs y el esquema del evento Kafka |
| [explicacion_docker_compose.md](docs/explicacion_docker_compose.md) | Cómo funciona el `docker-compose.yml`, servicio por servicio |
| [explicacion_modelo_mongo.md](docs/explicacion_modelo_mongo.md) | Modelo de documentos de `match-ingestion-service` y por qué NoSQL |
| [explicacion_mensajeria_kafka.md](docs/explicacion_mensajeria_kafka.md) | Productor y consumer de Kafka, esquema del evento, cómo demostrarlo |
| [explicacion_circuit_breaker.md](docs/explicacion_circuit_breaker.md) | Circuit Breaker (Resilience4J), cómo demostrar la apertura |
| [explicacion_seguridad.md](docs/explicacion_seguridad.md) | JWT, validación en el gateway, roles, endpoints internos, CORS y secretos |
| [explicacion_observabilidad.md](docs/explicacion_observabilidad.md) | Métricas (Prometheus/Grafana) y logs centralizados (Loki/Promtail), cómo demostrarlo |
| [explicacion_frontend.md](docs/explicacion_frontend.md) | Arquitectura de `frontend-app`, capa de API/sesión, qué mockup quedó fuera de alcance |
| [quien-juega.http](docs/quien-juega.http) | Colección de requests reproducible del flujo completo (ver **Cómo probar**) |

Convención de nombres dentro de `docs/`:

- `planificacion_global.md` — planificación general y seguimiento.
- `plan_*.md` — plan de implementación de una tarea concreta de la planificación global.
- `explicacion_*.md` — material para entender el proyecto y las decisiones tomadas.
- `assets/` — imágenes y mockups.
