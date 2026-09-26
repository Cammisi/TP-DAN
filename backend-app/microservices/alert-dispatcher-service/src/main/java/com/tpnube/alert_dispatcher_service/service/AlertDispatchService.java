package com.tpnube.alert_dispatcher_service.service;

import com.tpnube.alert_dispatcher_service.client.FanIdentityClient;
import com.tpnube.alert_dispatcher_service.client.dto.FanSubscriberDTO;
import com.tpnube.alert_dispatcher_service.client.dto.NotificationRequestDTO;
import com.tpnube.alert_dispatcher_service.dto.MatchEventDTO;
import com.tpnube.alert_dispatcher_service.service.FanIdentityLookupService.SubscribersUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

// Separado de MatchEventListener para poder testear con Mockito sin levantar Kafka.
@Service
public class AlertDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatchService.class);

    private final FanIdentityLookupService fanIdentityLookupService;
    private final FanIdentityClient fanIdentityClient;
    private final DispatcherStatusService dispatcherStatusService;

    public AlertDispatchService(FanIdentityLookupService fanIdentityLookupService,
                                 FanIdentityClient fanIdentityClient,
                                 DispatcherStatusService dispatcherStatusService) {
        this.fanIdentityLookupService = fanIdentityLookupService;
        this.fanIdentityClient = fanIdentityClient;
        this.dispatcherStatusService = dispatcherStatusService;
    }

    public void dispatch(MatchEventDTO event) {
        List<FanSubscriberDTO> subscribers;
        try {
            subscribers = fanIdentityLookupService.getSubscribers(event.teamId());
        } catch (SubscribersUnavailableException e) {
            log.warn("Evento {} del equipo {} no despachado: {}", event.eventId(), event.teamId(), e.getMessage());
            dispatcherStatusService.recordUndispatched(event, e.getMessage());
            return;
        }

        if (subscribers.isEmpty()) {
            log.info("Evento {} del equipo {} sin suscriptos", event.eventId(), event.teamId());
            dispatcherStatusService.recordProcessed(event, 0);
            return;
        }

        String message = buildMessage(event);
        int failures = 0;
        for (FanSubscriberDTO subscriber : subscribers) {
            try {
                fanIdentityClient.createNotification(new NotificationRequestDTO(
                        subscriber.fanId(),
                        event.eventId(),
                        event.matchId(),
                        event.teamId(),
                        message,
                        event.occurredAt()
                ));
            } catch (RuntimeException e) {
                // Un fan que falla no le saca la notificación a los demás.
                failures++;
                log.error("Falló la notificación al fan {} del evento {}: {}",
                        subscriber.fanId(), event.eventId(), e.getMessage());
            }
        }

        int notified = subscribers.size() - failures;
        if (notified == 0) {
            // Que fallen todos los callbacks es, para el panel de admin, lo mismo que no despachar.
            dispatcherStatusService.recordUndispatched(event,
                    "Fallaron los " + failures + " callbacks de notificación");
        } else {
            dispatcherStatusService.recordProcessed(event, notified);
        }
        log.info("Evento {}: {} notificaciones creadas, {} fallidas",
                event.eventId(), notified, failures);
    }

    private String buildMessage(MatchEventDTO event) {
        return switch (event.type()) {
            case "GOAL" -> "¡Gol! %s (%d') — marcador %d-%d".formatted(
                    event.playerName(), event.minute(), event.homeScore(), event.awayScore());
            case "CARD" -> "Tarjeta %s para %s (%d')".formatted(
                    "RED".equals(event.cardType()) ? "roja" : "amarilla", event.playerName(), event.minute());
            case "MATCH_END" -> "Final del partido — resultado %d-%d".formatted(
                    event.homeScore(), event.awayScore());
            default -> event.description() != null
                    ? event.description()
                    : "Nuevo evento en el partido " + event.matchId();
        };
    }
}
