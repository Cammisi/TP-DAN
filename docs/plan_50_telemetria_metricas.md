# Plan de implementación — T-50 · Telemetría de métricas de rendimiento

Tarea de Fase 3, transversal a los 5 servicios Spring y a `docker-compose.yml`.

## 1. Contexto y Objetivos

El enunciado exige telemetría de métricas de rendimiento y estado del sistema. En esta tarea se instrumentan los 5 servicios con Spring Boot Actuator y Micrometer Prometheus, se integran Prometheus y Grafana en el Docker Compose con un dashboard preconfigurado, y se migran los healthchecks TCP a `/actuator/health` (cerrando el riesgo R-6).

## 2. Requisitos a satisfacer

1. **Exposición de métricas:** Los 5 servicios exponen `/actuator/prometheus` en la red privada de Docker (`tp-nube-network`).
2. **Scraping centralizado:** Prometheus scrapea a los 5 servicios periódicamente (cada 5s / 10s).
3. **Visualización en Grafana:** Grafana arranca con datasource de Prometheus autoprovisionado y un dashboard que visualiza:
   - Volumen y latencia de peticiones HTTP (`http_server_requests_seconds_count` y `http_server_requests_seconds_sum`).
   - Uso de memoria JVM Heap y Non-Heap (`jvm_memory_used_bytes`, `jvm_memory_max_bytes`).
   - Estado del Circuit Breaker de Resilience4J (`resilience4j_circuitbreaker_state`).
4. **Healthchecks HTTP reales (R-6):** Los healthchecks de `docker-compose.yml` para los 5 servicios pasan de inspección TCP en bash a `curl -f http://localhost:<port>/actuator/health || exit 1`.
5. **Aislamiento de seguridad (advertencia de T-40):**
   - En `cloud-gateway`: los endpoints de Actuator se configuran en un puerto de gestión dedicado `management.server.port: 8084`. Esto impide que queden expuestos al host a través del puerto público 8080 y evita colisionar con el filtro JWT (`JwtValidationFilter`).
   - En `fan-identity-service`: se habilita `/actuator/**` en `SecurityConfig` para permitir el scraping dentro de la red Docker sin requerir JWT.
   - Ningún servicio expone su puerto de Actuator al host excepto los puertos propios de Prometheus (9090) y Grafana (3001).
6. **Puerto de Grafana:** Se mapea a `3001:3000` en el host para dejar el puerto `3000` disponible para el frontend Next.js (T-60) y su CORS configurado en T-40.

## 3. Componentes a modificar o crear

### A. Dependencias (`pom.xml`)
- `cloud-gateway`: agregar `io.micrometer:micrometer-registry-prometheus` (ya posee `spring-boot-starter-actuator`).
- `eureka-server`: agregar `spring-boot-starter-actuator` y `io.micrometer:micrometer-registry-prometheus`.
- `fan-identity-service`: agregar `spring-boot-starter-actuator` y `io.micrometer:micrometer-registry-prometheus`.
- `match-ingestion-service`: agregar `spring-boot-starter-actuator` y `io.micrometer:micrometer-registry-prometheus`.
- `alert-dispatcher-service`: agregar `spring-boot-starter-actuator` y `io.micrometer:micrometer-registry-prometheus`.

### B. Configuración de servicios (`application.yaml`)
En los 5 servicios:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```
En `cloud-gateway`:
```yaml
management:
  server:
    port: 8084
```
En `fan-identity-service` (`SecurityConfig.java`):
Permitir `/actuator/**` en el `authorizeHttpRequests`.

### C. Imágenes Docker (`Dockerfile`)
Instalar `curl` en la etapa de runtime de los 5 microservicios (`eclipse-temurin:25-jre-jammy`) para soportar los healthchecks HTTP.

### D. Infraestructura de Monitoreo (`docker-compose.yml`)
- **`infrastructure/prometheus/prometheus.yml`**: targets para los 5 servicios Spring.
- **`infrastructure/grafana/provisioning/datasources/prometheus.yml`**: datasource automático apuntando a `http://prometheus:9090`.
- **`infrastructure/grafana/provisioning/dashboards/dashboards.yml`**: proveedor de dashboards.
- **`infrastructure/grafana/dashboards/tp-dan-dashboard.json`**: dashboard con los paneles de métricas exigidos.
- Actualización de los 5 healthchecks a `curl -f http://localhost:<port>/actuator/health`.
- Documentación de cambios en `docs/explicacion_docker_compose.md`.

## 4. Plan de verificación

1. Levantar el stack completo: `docker compose up --build -d`.
2. Verificar que los 11 contenedores (los 9 actuales + prometheus + grafana) queden `healthy` / `Up`.
3. Validar scraping de Prometheus: acceder a `http://localhost:9090/targets` y ver los 5 endpoints en estado `UP`.
4. Validar dashboard de Grafana: acceder a `http://localhost:3001` (admin/admin), abrir el dashboard "TP-DAN — Métricas de Rendimiento".
5. Generar tráfico: ejecutar el recorrido del fan de T-42 y constatar cómo aumentan los contadores de peticiones HTTP, la memoria JVM y el estado del Circuit Breaker.
6. Validar seguridad: constatar que `http://localhost:8080/actuator` no es accesible desde el exterior.
