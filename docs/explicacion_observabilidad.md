# Observabilidad: métricas y logs centralizados

Explica los dos requisitos obligatorios del enunciado que cubre esta parte: telemetría de métricas de rendimiento (T-50) y almacén centralizado de logs (T-51). Decisión de stack en D-4 de `docs/planificacion_global.md`.

## 1. Los cuatro roles, sin confundirlos

Es la confusión más común del tema, así que conviene tenerla clara para el coloquio:

| Componente | Qué hace | Qué NO hace |
|---|---|---|
| **SLF4J/Logback** (en cada servicio) | Escribe los logs a la salida estándar del contenedor | No los centraliza ni los almacena |
| **Micrometer + Actuator** (en cada servicio) | Expone las métricas en formato Prometheus en `/actuator/prometheus` | No las guarda, solo las expone para que alguien las lea |
| **Prometheus** | Scrapea (hace polling) esas métricas cada 5 s y las guarda con su timestamp | No guarda texto de log, solo números |
| **Loki** | Almacena los logs que le manda Promtail, indexados por *labels* (no por texto completo) | No calcula métricas |
| **Promtail** | Lee los logs de los contenedores (vía el socket de Docker) y se los manda a Loki | No los transforma más que etiquetarlos |
| **Grafana** | Consulta Prometheus y Loki y los grafica/lista | No almacena nada — es solo la ventana |

El "almacén centralizado de logs" que pide el enunciado lo cumple **Loki**, no Grafana ni SLF4J.

## 2. Métricas (T-50)

### 2.1 Cómo llegan las métricas a Prometheus

Los 5 servicios Spring tienen `spring-boot-starter-actuator` + `micrometer-registry-prometheus` y exponen `/actuator/prometheus`. `infrastructure/prometheus/prometheus.yml` los scrapea cada 5 segundos:

```yaml
scrape_configs:
  - job_name: 'fan-identity-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['fan-identity-service:8081']
  # ... un job igual por cada uno de los otros 4 servicios
```

### 2.2 El caso particular de `cloud-gateway`

Desde T-40, el gateway exige JWT para todo lo que no sea `/api/fans/auth/**` — **incluido su propio `/actuator/**`**. Si Prometheus intentara scrapear `cloud-gateway:8080/actuator/prometheus` como a los demás, el `JwtValidationFilter` le respondería `401`.

La solución: `cloud-gateway` mueve Actuator a un puerto de management separado (`management.server.port: 8084`) que **no está declarado en `ports:` de `docker-compose.yml`**, así que no es alcanzable desde el host, solo desde dentro de la red Docker. Prometheus scrapea `cloud-gateway:8084/actuator/prometheus` directamente, sin pasar por el filtro de seguridad del puerto 8080. El healthcheck del propio contenedor usa el mismo puerto 8084 por la misma razón.

### 2.3 Qué muestra el dashboard

`infrastructure/grafana/dashboards/tp-dan-dashboard.json` se aprovisiona solo al levantar Grafana (no hay que importarlo a mano). Paneles:

- **Tasa de peticiones HTTP (req/s)** y **latencia promedio** — de `http_server_requests_seconds_count`/`_sum`, por servicio.
- **Uso de memoria JVM Heap (MB)** — de `jvm_memory_used_bytes{area="heap"}`.
- **Estado del Circuit Breaker (Resilience4J)** — de `resilience4j_circuitbreaker_state`, el mismo dato que expone `GET /api/alerts/status` (T-31) pero como serie de tiempo.

### 2.4 Cómo demostrarlo

1. Generar tráfico: el recorrido de `docs/quien-juega.http` (T-71) alcanza.
2. Abrir Grafana (`http://localhost:3001`, `admin`/`admin`) → dashboard **"TP-DAN — Métricas de Rendimiento"**.
3. Apagar `fan-identity-service` e ingestar eventos (mismo procedimiento que `docs/explicacion_circuit_breaker.md`) para ver el panel de Circuit Breaker pasar a `OPEN` en vivo.
4. `curl http://localhost:8080/actuator/health` (puerto público del gateway) → `401`, para mostrar que Actuator no quedó expuesto.

## 3. Logs centralizados (T-51)

### 3.1 Cómo llegan los logs a Loki

`promtail` lee los logs de **todos** los contenedores a través del socket de Docker (`docker_sd_configs`, no hace falta declarar cada contenedor a mano) y se los manda a Loki. Dos etiquetas (*labels*) se extraen en el camino, ambas indexadas (se puede filtrar por ellas en Grafana sin escanear todo el texto):

```yaml
relabel_configs:
  - source_labels: ['__meta_docker_container_name']
    regex: '/(.*)'
    target_label: 'container'
  - source_labels: ['container']
    target_label: 'service'
pipeline_stages:
  - docker: {}
  - regex:
      expression: '^[^\s]+\s+(?P<level>INFO|WARN|ERROR|DEBUG|TRACE)\s+'
  - labels:
      level:
```

`service` viene del nombre del contenedor (`fan-identity-service`, `cloud-gateway`, etc.) y `level` se extrae con una regex del formato de línea que ya imprime Logback (`INFO`, `WARN`, `ERROR`...). Ninguno de los dos requiere tocar código de los microservicios.

### 3.2 El identificador de correlación

El enunciado pide poder seguir un evento a través de los servicios. Acá **no hay un filtro de trace-id dedicado** (tipo Sleuth/MDC): el identificador de correlación es el `eventId` del evento deportivo, que ya viaja de punta a punta por diseño (T-01) y que cada servicio loguea naturalmente en el punto donde lo toca:

- `match-ingestion-service`, al publicarlo en Kafka.
- `alert-dispatcher-service`, al recibirlo (`MatchEventListener`) y al despacharlo (`AlertDispatchService`).
- `fan-identity-service`, al persistir la notificación (`NotificationService.createNotification`).

Alcanza con buscar el `eventId` como texto en Grafana Explore, filtrando por `service` para ver el orden exacto en que pasó por cada uno.

### 3.3 Cómo demostrarlo

1. Ingestar un evento y anotar el `eventId` que devuelve `POST /api/matches/{matchId}/events`.
2. Grafana (`http://localhost:3001`) → **Explore** → datasource **Loki**.
3. Query: `{service=~"match-ingestion-service|alert-dispatcher-service|fan-identity-service"} |= "<eventId>"`.
4. Se ven las tres líneas, en orden cronológico, una por servicio.
5. Para filtrar por nivel: agregar `| level="ERROR"` a la misma query (o elegir el label `level` desde el selector de Grafana).

### 3.4 Qué no debería aparecer

Ninguna contraseña, token ni dato sensible: las contraseñas nunca se loguean (van hasheadas y ni así se imprimen), los JWT tampoco. El único log "ruidoso" es el de arranque de Spring Security, que imprime una contraseña random de desarrollo si no hay ningún `UserDetailsService` configurado explícitamente — no es un secreto real, es el que Spring genera cuando no encuentra otra configuración, y no se usa para nada porque `SecurityConfig` define su propia cadena de filtros.

## 4. Limitaciones aceptadas

- **Sin trace-id dedicado:** la correlación depende de que el `eventId` (u otro dato de negocio) aparezca en el mensaje de log; no hay un ID técnico que ate *todas* las requests entre sí (por ejemplo, un login no queda correlacionado con nada).
- **Sin alertas configuradas:** Prometheus y Grafana están para mirar, no hay reglas de alerta ni notificaciones si algo se degrada.
- **Retención por defecto:** ni Prometheus ni Loki tienen una política de retención ajustada para el TP; usan los defaults de la imagen.
