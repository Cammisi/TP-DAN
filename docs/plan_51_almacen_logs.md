# Plan de implementación — T-51 · Almacén centralizado de logs

Tarea de Fase 3, transversal a los servicios y a `docker-compose.yml`.

## 1. Contexto y Objetivos

El enunciado exige un almacén centralizado de logs para auditar y monitorear la plataforma. En esta tarea se incorporan **Loki** y **Promtail** en Docker Compose, se configura el datasource de Loki en Grafana, se estandariza la recolección y etiquetado de logs por servicio y nivel (`INFO`, `WARN`, `ERROR`), y se asegura que el identificador de correlación (`eventId`) viaje y se registre en los tres microservicios principales (`match-ingestion-service`, `alert-dispatcher-service` y `fan-identity-service`) desde la ingesta hasta la notificación al fan.

## 2. Requisitos a satisfacer

1. **Recolección automática:** Promtail recolecta los logs de todos los contenedores de Docker a través del socket de Docker (`/var/run/docker.sock`) y los almacena en Loki.
2. **Filtrado en Grafana:** Desde el panel Explore o dashboards de Grafana se pueden filtrar los logs por etiqueta de servicio (`container`/`service`) y nivel (`level: INFO, WARN, ERROR`).
3. **Identificador de correlación (`eventId`):**
   - `match-ingestion-service`: emite log estructurado con `eventId` al persistir y publicar el evento.
   - `alert-dispatcher-service`: emite log con `eventId` al recibir de Kafka, al consultar suscriptores y al despachar las alertas.
   - `fan-identity-service`: emite log con `eventId` al crear la notificación para el fan.
   - Filtrar `{service=~".+"} |= "<eventId>"` en Loki muestra el ciclo de vida completo del evento a través de los tres microservicios.
4. **Seguridad y privacidad:** No se loguean contraseñas, tokens JWT completos ni secretos internos.
5. **Datasource en Grafana:** Datasource de Loki aprovisionado automáticamente en Grafana junto al datasource de Prometheus existente.

## 3. Componentes a crear o modificar

### A. Microservicios (Logging con `eventId`)
- **`match-ingestion-service` (`MatchEventService.java`)**:
  - Agregar logger SLF4J y loguear: `log.info("Evento ingestado exitosamente: eventId={}, matchId={}, teamId={}, type={}", event.getId(), match.getId(), event.getTeamId(), type);`
- **`fan-identity-service` (`NotificationService.java`)**:
  - Agregar logger SLF4J y loguear: `log.info("Notificación persistida: id={}, fanId={}, eventId={}, matchId={}", notification.getId(), fanId, eventId, matchId);`
- **`alert-dispatcher-service` (`MatchEventListener.java` y `AlertDispatchService.java`)**:
  - Ya loguean `event.eventId()`; se verificará consistencia en los mensajes.

### B. Infraestructura de Logs
- **`infrastructure/loki/loki-config.yml`**: Configuración estándar de Loki 3.x con almacenamiento filesystem.
- **`infrastructure/promtail/promtail-config.yml`**: Configuración de Promtail con `docker_sd_configs`, extracción de campos JSON y extracción de etiqueta `level`.
- **`infrastructure/grafana/provisioning/datasources/loki.yml`**: Configuración de datasource Loki en Grafana apuntando a `http://loki:3100`.

### C. Docker Compose y Documentación
- **`docker-compose.yml`**:
  - Agregar servicio `loki` (`grafana/loki:3.1.0`) en puerto `3100:3100`.
  - Agregar servicio `promtail` (`grafana/promtail:3.1.0`) con volúmenes para `/var/run/docker.sock` y `/var/lib/docker/containers`.
  - Actualizar `grafana` para que dependa de `loki` además de `prometheus`.
- **`docs/explicacion_docker_compose.md`**: Documentar los nuevos servicios `loki` y `promtail`.

## 4. Plan de verificación

1. Levantar el stack completo con `docker compose up -d --build`.
2. Verificar que los 13 contenedores estén corriendo (`healthy` / `Up`).
3. Verificar que Loki esté listo en `http://localhost:3100/ready`.
4. Verificar en Grafana (`http://localhost:3001` -> Explore) que el datasource Loki está activo y lista los logs de los contenedores.
5. Ingestar un evento deportivo a través del gateway (`POST /api/matches/match-001/events`) y capturar el `eventId` resultante.
6. Ejecutar en Grafana / Loki la consulta de logs filtrando por dicho `eventId`:
   `{service=~".+"} |= "<eventId>"`
   y constatar que aparecen en orden cronológico los logs de:
   1. `match-ingestion-service`
   2. `alert-dispatcher-service`
   3. `fan-identity-service`
7. Verificar que el filtro por nivel (ej. `level="INFO"`) funciona correctamente.
