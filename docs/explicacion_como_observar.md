# Cómo observar el sistema

Guía práctica para ver funcionando ¿Quién Juega? con las herramientas del stack, pensada para quien nunca usó Eureka, Kafka, Grafana ni Prometheus. Acá está dónde hacer clic y qué mirar; cómo está configurada cada herramienta y por qué se eligió está en `explicacion_observabilidad.md`, `explicacion_mensajeria_kafka.md` y `explicacion_circuit_breaker.md`.

## 1. Antes de empezar

Todo se observa desde el navegador o desde una terminal, sin instalar nada. Primero confirmá que el stack está arriba: `docker compose ps` tiene que mostrar los 14 contenedores en `running`.

| Herramienta | Qué te muestra | Dónde |
|---|---|---|
| Eureka | Qué microservicios están vivos y registrados | http://localhost:8761 |
| Kafka UI | Los mensajes que match-ingestion le deja a alert-dispatcher | http://localhost:8090 |
| Grafana | Gráficos de métricas y búsqueda de logs de todos los servicios | http://localhost:3001 |
| Prometheus | Las métricas crudas y si llega a cada servicio | http://localhost:9090 |
| Terminal (Docker) | Logs en vivo, estado de los contenedores y las bases de datos | PowerShell en `c:\dev\TP-DAN` |
| La app | Lo que ven el fan y el admin | http://localhost:3000 |

Grafana pide usuario `admin` y clave `admin`. En la app, el fan de prueba es `fan1@quienjuega.com` / `fan123` y el admin es `admin@quienjuega.com` / `admin123`.

Loki, el almacén de logs, no tiene pantalla propia: se consulta desde Grafana. Grafana tampoco guarda nada; solo muestra lo que tienen Prometheus (números) y Loki (texto de los logs).

El recorrido de un gol y dónde mirar cada tramo (entre paréntesis):

```
Admin en /admin ──▶ cloud-gateway ──▶ match-ingestion ──publica──▶ Kafka: match-events
(la app)            (Eureka)          (Loki, mongosh)              (Kafka UI)
                                                                        │ consume
                                                                        ▼
Fan en /alerts ◀── fan-identity ◀─────────────────────────────── alert-dispatcher
(la app)           (Loki, psql)                                   (Loki, estado del despachador)

Para todos los servicios a la vez:
  Métricas: servicios Spring ──▶ Prometheus ──▶ Grafana (dashboard TP-DAN)
  Logs:     todos los contenedores ──▶ Promtail ──▶ Loki ──▶ Grafana (Explore)
```

El admin espera solo hasta que match-ingestion publica en Kafka; desde ahí, el resto del recorrido corre sin él.

## 2. Eureka: qué servicios están vivos

Eureka es la guía de direcciones del sistema: con el stack sano, muestra 4 servicios en `UP`.

**Cómo funciona.** Cada servicio, al arrancar, le avisa a Eureka "estoy vivo en tal dirección" y repite ese aviso cada 30 s. Cuando el gateway necesita llamar a un servicio, le pregunta a Eureka dónde está. Por eso ningún servicio tiene la dirección de otro escrita a mano.

**Qué mirar.** Entrá a http://localhost:8761 y buscá la tabla **Instances currently registered with Eureka**. Tienen que estar estas 4 filas, cada una con estado `UP (1)`; el 1 es la cantidad de instancias:

- `ALERT-DISPATCHER-SERVICE`
- `CLOUD-GATEWAY`
- `FAN-IDENTITY-SERVICE`
- `MATCH-INGESTION-SERVICE`

**Probá esto.** Apagá un servicio y mirá cómo desaparece:

```powershell
docker compose stop match-ingestion-service    # refrescá Eureka: la fila desaparece
docker compose start match-ingestion-service   # en 30-60 s vuelve a aparecer
```

**Por consola.** En PowerShell, `curl` es un alias de otro comando; para el curl real escribí `curl.exe`:

```powershell
curl.exe -s http://localhost:8761/eureka/apps
```

Si ves un cartel rojo que dice **EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP**, no es un error: es un modo de protección de Eureka que aparece seguido en desarrollo.

## 3. Kafka UI: los mensajes entre servicios

Kafka es el buzón entre match-ingestion y alert-dispatcher: un evento queda guardado ahí aunque el despachador esté apagado, y le llega cuando vuelve.

Entrá a http://localhost:8090. A la izquierda está el cluster **tp-nube-deportes**, con sus secciones Brokers, Topics y Consumers.

**Topics → `match-events`.** Es el buzón donde match-ingestion publica cada evento. Tiene 3 particiones, como 3 carriles paralelos. En la pestaña **Messages** está cada evento publicado; hacé clic en uno para expandirlo:

- **Key**: el `teamId`. Todos los eventos de un mismo equipo van siempre a la misma partición, y así se procesan en orden.
- **Value**: el JSON del evento (tipo, minuto, jugador, marcador).
- **Partition** y **Offset**: en qué carril quedó y en qué posición.

**Consumers → `alert-dispatcher-service`.** Es quien lee del buzón.

- **State `STABLE`**: el consumer está conectado. `EMPTY` significa que no hay nadie leyendo.
- **Consumer Lag**: cuántos mensajes le faltan leer. `0` significa que está al día.

**Probá esto.** Es la mejor demostración de para qué sirve la mensajería asincrónica:

1. `docker compose stop alert-dispatcher-service`
2. Como admin, creá un gol en `/admin`. Igual responde bien, porque match-ingestion no depende del despachador.
3. En Consumers, el lag pasa a `1`: el mensaje está guardado, esperando.
4. `docker compose start alert-dispatcher-service`. Cuando arranca, el lag vuelve a `0` y la alerta llega igual.

**Por consola.** Estos comandos corren las herramientas de Kafka adentro de su contenedor:

```powershell
# Estado del consumer: offset actual, último offset y LAG por partición
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group alert-dispatcher-service

# Todos los mensajes del tópico con su key (Ctrl+C para salir)
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic match-events --from-beginning --property print.key=true
```

En el primero, `CURRENT-OFFSET` es hasta dónde leyó el despachador, `LOG-END-OFFSET` es el último mensaje que hay, y `LAG` es la diferencia.

## 4. Grafana: el dashboard de métricas

El dashboard muestra en vivo cuánto trabaja cada servicio; un request aislado no se nota, así que para ver movimiento hay que generar tráfico.

Entrá a http://localhost:3001 con `admin` / `admin`. Si te pide cambiar la clave, tocá **Skip**. Después: menú **☰** (arriba a la izquierda) → **Dashboards** → **TP-DAN — Métricas de Rendimiento**. Muestra los últimos 15 minutos y se refresca solo cada 5 s; el rango se cambia arriba a la derecha.

| Panel | Qué significa |
|---|---|
| Tasa de Peticiones HTTP (req/s) | Cuántos requests por segundo recibe cada servicio |
| Latencia Promedio de Peticiones HTTP | Cuánto tarda cada servicio en responder |
| Uso de Memoria JVM Heap (MB) | La memoria que usa cada servicio Java |
| Estado de Circuit Breaker (Resilience4J) | Una línea por estado (`closed`, `open`, `half_open`…); la que vale 1 es el estado actual |

**Por qué un evento no mueve los gráficos.** El panel de requests es un promedio por segundo sobre el último minuto: un request aislado vale 0,017 req/s y no se ve. Prometheus sí lo cuenta (se ve en la sección 5).

**Generá tráfico.** Este comando hace 50 logins seguidos desde PowerShell:

```powershell
1..50 | ForEach-Object { Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/fans/auth/login -ContentType 'application/json' -Body '{"email":"fan1@quienjuega.com","password":"fan123"}' | Out-Null }
```

En menos de un minuto vas a ver subir las curvas de `cloud-gateway` y `fan-identity-service` en los paneles de requests y latencia.

**El panel del Circuit Breaker** se mueve solo cuando fan-identity falla varias veces seguidas. Para verlo pasar a `open`, seguí la demo de `explicacion_circuit_breaker.md`, sección 5: apagás fan-identity, cargás 6 goles del mismo equipo y lo volvés a prender.

## 5. Grafana Explore: buscar logs y métricas

Explore es donde le hacés preguntas a Loki (logs) y a Prometheus (números); la consulta más útil es la que sigue un evento por los tres servicios.

**Cómo se usa.**

1. Menú **☰** → **Explore**.
2. Arriba a la izquierda, elegí el datasource: **Loki** o **Prometheus**.
3. En la caja de consulta, a la derecha, pasá de **Builder** a **Code**.
4. Pegá la consulta y tocá **Run query** (el botón azul de arriba a la derecha).
5. Si no aparece nada, ampliá el rango de tiempo (por ejemplo, **Last 3 hours**).

**Consultas para Loki.** Cada log llega etiquetado con `service` (el nombre del contenedor) y `level` (`INFO`, `WARN`, `ERROR`), y se puede filtrar por esas etiquetas:

| Consulta | Qué muestra |
|---|---|
| `{service="alert-dispatcher-service"}` | Todos los logs del despachador |
| `{service="alert-dispatcher-service", level="WARN"}` | Solo las advertencias, como un timeout del Circuit Breaker |
| `{service=~"match-ingestion-service\|alert-dispatcher-service\|fan-identity-service"} \|= "EL_EVENT_ID"` | El recorrido de un evento por los tres servicios |

La última es la que pide el enunciado: seguir un evento entre servicios. Reemplazá `EL_EVENT_ID` por un id real, que aparece en los logs del despachador ("Evento recibido: …") o en el JSON del mensaje en Kafka UI. Vas a ver, en orden:

1. match-ingestion: "Evento ingestado exitosamente" y "publicado en match-events".
2. alert-dispatcher: "Evento recibido" y el resultado del despacho.
3. fan-identity: la notificación guardada, si alguien sigue a ese equipo.

El botón **Live**, arriba, muestra los logs en vivo a medida que llegan.

**Consultas para Prometheus.**

| Consulta | Qué muestra |
|---|---|
| `up` | 1 si Prometheus llega a cada servicio, 0 si está caído |
| `http_server_requests_seconds_count{job="match-ingestion-service"}` | Requests acumulados por endpoint y código de respuesta |
| `resilience4j_circuitbreaker_state{state="open"}` | 1 si el circuito está abierto |

Arriba del resultado podés alternar entre **Table** (el valor actual) y **Graph** (la curva en el tiempo).

## 6. Prometheus directo

Prometheus es el que recolecta las métricas: cada 5 s le pide sus números a los 5 servicios Spring. Su pantalla propia sirve sobre todo para confirmar que llega a todos.

Entrá a http://localhost:9090.

- **Status → Targets**: los 5 servicios que consulta (`alert-dispatcher-service`, `cloud-gateway`, `eureka-server`, `fan-identity-service`, `match-ingestion-service`), todos en `UP`. Si un gráfico de Grafana queda vacío, este es el primer lugar a revisar.
- **Graph**: escribís cualquiera de las consultas de Prometheus de la sección 5 y tocás **Execute**. Es lo mismo que Explore en Grafana, con una pantalla más simple.

Cada servicio expone sus números en `/actuator/prometheus`. En el gateway eso vive en un puerto interno (8084), por eso `http://localhost:8080/actuator/health` responde `401`: es lo esperado, no un error.

## 7. Por consola, con Docker

Desde una terminal en `c:\dev\TP-DAN` ves lo mismo que en las pantallas, más el contenido de las bases de datos.

**Contenedores y logs.**

```powershell
docker compose ps                                   # estado de los 14 contenedores
docker compose logs -f alert-dispatcher-service     # logs en vivo (Ctrl+C para salir)
docker compose logs --since 10m match-ingestion-service
docker stats                                        # CPU y RAM de cada contenedor (Ctrl+C para salir)
```

Los logs están en hora UTC, 3 horas adelante de la hora de Argentina.

**Estado del despachador.** Devuelve los últimos eventos procesados, los que no se pudieron despachar y el estado del circuito:

```powershell
docker exec alert-dispatcher-service curl -s localhost:8083/api/alerts/status
```

Adentro del contenedor no hace falta token. Desde afuera, por el gateway, `GET /api/alerts/status` exige un token de admin.

**PostgreSQL**, la base de fan-identity (fans, equipos, suscripciones y notificaciones):

```powershell
docker exec -it postgres psql -U fan_admin -d fan_identity_db
```

Adentro:

- `\dt` lista las tablas.
- `select * from subscriptions;` muestra quién sigue a qué equipo.
- `select * from notifications order by id desc limit 5;` muestra las últimas alertas guardadas.
- `\q` sale.

**MongoDB**, la base de match-ingestion (partidos y eventos):

```powershell
docker exec -it mongo mongosh match_ingestion_db
```

Adentro:

- `show collections` lista las colecciones (`matches` y `match_events`).
- `db.matches.find()` muestra los partidos.
- `db.match_events.find().sort({_id:-1}).limit(3)` muestra los últimos 3 eventos.
- `exit` sale.

## 8. Ejercicio: un gol visto en todas las herramientas

Un mismo gol aparece en cinco lugares en cuestión de segundos; seguirlo de punta a punta es la mejor forma de entender el sistema.

Preparación: el fan que uses tiene que seguir a uno de los dos equipos del partido. Verificalo en `/subscriptions`.

1. Abrí cuatro pestañas: Kafka UI (Topics → `match-events` → Messages), Grafana Explore con Loki, `/alerts` logueado como el fan, y una terminal con `docker compose logs -f alert-dispatcher-service`.
2. En otra ventana, logueado como admin, creá un gol en `/admin` del equipo que sigue el fan.
3. **Terminal:** aparece "Evento recibido: `<eventId>` (GOAL) del equipo `<teamId>`". Copiá ese id.
4. **Kafka UI:** refrescá Messages. El mensaje nuevo tiene como key el `teamId` del equipo. En Consumers, el lag sigue en `0`.
5. **Loki:** pegá la consulta del recorrido con ese `eventId`. Vas a ver los tres servicios en orden: match-ingestion, alert-dispatcher y fan-identity.
6. **Postgres:** `select * from notifications order by id desc limit 1;` muestra la notificación guardada, con el mismo `event_id`.
7. **App:** en `/alerts`, la alerta nueva. Si no aparece, tocá **Actualizar**.

Si en el paso 3 ves "no despachado" en vez del resultado, mirá la sección 9.

## 9. Problemas conocidos y cosas que no son errores

La mayoría de las "fallas" que vas a ver son comportamientos esperados; una alerta solo se pierde si el despachador no logra consultar a fan-identity, algo que pasa sobre todo con el primer evento después de levantar el stack.

| Qué ves | Qué significa | Qué hacer |
|---|---|---|
| `401` en `http://localhost:8080/actuator/health` | El gateway rechaza todo lo que no trae token; su Actuator vive en el puerto interno 8084 | Nada, es lo esperado |
| Cartel rojo "EMERGENCY!" en Eureka | Modo de protección de Eureka, habitual en desarrollo | Nada |
| Loki responde `503` | Está arrancando | Esperar unos segundos |
| El gráfico de requests no se mueve con un evento | Un request aislado es 0,017 req/s | Generar tráfico (sección 4) |
| "Evento … no despachado" en los logs del despachador | La consulta de suscriptores a fan-identity falló o tardó más de 3 s; pasa sobre todo con el primer evento después de levantar el stack | Crear otro evento: el que falló no se reintenta solo |
| La alerta tarda varios minutos en llegar | Kafka recreó el tópico y el consumer tardó en ver sus 3 particiones (R-14, pendiente en T-74) | Esperar ~5 minutos después de un `up`, o mandar un evento de prueba antes de la demo |
| `curl` en PowerShell se comporta raro | En PowerShell, `curl` es un alias de `Invoke-WebRequest` | Usar `curl.exe` |
| Las horas de los logs no coinciden con tu reloj | Los logs están en UTC | Restar 3 horas |
| `dependency failed to start: container … is unhealthy` al levantar | Un contenedor tardó más que su healthcheck al arrancar todo junto | Correr `docker compose up -d` de nuevo |

Para la demo del coloquio: después de `docker compose up`, esperá 1 o 2 minutos y mandá un evento de prueba antes de mostrar el flujo en vivo.
