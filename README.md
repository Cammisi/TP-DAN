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
| **fan-identity-service** | Registro, login, JWT, perfiles y suscripciones (fan ↔ equipo) | PostgreSQL |
| **match-ingestion-service** | Ingesta y valida eventos de partidos desde proveedores externos, los persiste y los publica en Kafka | MongoDB |
| **alert-dispatcher-service** | Consume eventos de Kafka, consulta a fan-identity-service quiénes están suscritos y genera las notificaciones | Sin base propia (Single Source of Truth) |
| **eureka-server** | Service discovery | — |
| **cloud-gateway** | Punto de entrada único, enruta por load balancing hacia los microservicios vía Eureka | — |

La llamada de `alert-dispatcher-service` a `fan-identity-service` está protegida con **Circuit Breaker** (Resilience4j): si fan-identity-service cae o responde con latencia alta, el circuito se abre para evitar que alert-dispatcher-service colapse esperando respuestas.

## Roles de usuario

- **Fan:** se registra/inicia sesión, busca equipos y ligas, gestiona sus suscripciones (seguir/dejar de seguir) y consulta su historial de alertas.
- **Administrador:** monitorea el estado de los microservicios y puede simular la carga de un evento deportivo para disparar el flujo completo de punta a punta.

## Tecnologías

- **Backend:** Spring Boot 4.1, Spring Cloud 2025.1.2 (Eureka, Gateway MVC, OpenFeign, Circuit Breaker/Resilience4j), Spring Security + JWT, Java 25.
- **Mensajería:** Apache Kafka (modo KRaft, sin ZooKeeper).
- **Persistencia:** PostgreSQL (fan-identity-service), MongoDB (match-ingestion-service).
- **Infraestructura:** Docker / Docker Compose.

## Estructura del repositorio

```
tp-nube-deportes/
├── backend-app/
│   ├── infrastructure/
│   │   ├── eureka-server/       # Service Discovery — puerto 8761
│   │   └── cloud-gateway/       # API Gateway — puerto 8080
│   └── microservices/
│       ├── fan-identity-service/       # puerto 8081
│       ├── match-ingestion-service/    # puerto 8082
│       └── alert-dispatcher-service/   # puerto 8083
├── frontend-app/                       # Cliente web (consume únicamente el gateway)
├── docker-compose.yml
└── CLAUDE.md                           # Notas técnicas de desarrollo
```

## Cómo levantar el proyecto

Requisitos: Docker y Docker Compose.

```bash
# Compilar todas las imágenes y levantar el stack completo
docker compose up --build

# Levantar en segundo plano
docker compose up --build -d

# Apagar
docker compose down
```

## Puertos y paneles

| Servicio | Puerto | Descripción |
|---|:---:|---|
| cloud-gateway | 8080 | Único puerto que debe consumir el frontend |
| eureka-server | 8761 | Dashboard de registro de servicios |
| PostgreSQL | 5432 | Base de datos de fan-identity-service |
| MongoDB | 27017 | Base de datos de match-ingestion-service |
| Kafka | 9092 | Broker de mensajería |
| Kafka UI | 8090 | Panel web de administración de tópicos y consumers |

Para el detalle completo de configuración, variables de entorno y decisiones técnicas, ver [CLAUDE.md](CLAUDE.md).
