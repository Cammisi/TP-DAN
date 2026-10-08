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
import java.util.Locale;

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

    // notifiedTeamId (la key del mensaje) puede no ser el equipo que hizo el evento: ver D-7.
    public void dispatch(MatchEventDTO event, String notifiedTeamId) {
        if (notifiedTeamId == null || notifiedTeamId.isBlank()) {
            log.warn("Evento {} sin equipo a notificar (mensaje sin key): no se despacha", event.eventId());
            dispatcherStatusService.recordUndispatched(event, notifiedTeamId,
                    "El mensaje no indica a qué equipo notificar");
            return;
        }

        List<FanSubscriberDTO> subscribers;
        try {
            subscribers = fanIdentityLookupService.getSubscribers(notifiedTeamId);
        } catch (SubscribersUnavailableException e) {
            log.warn("Evento {} para el equipo {} no despachado: {}", event.eventId(), notifiedTeamId, e.getMessage());
            dispatcherStatusService.recordUndispatched(event, notifiedTeamId, e.getMessage());
            return;
        }

        if (subscribers.isEmpty()) {
            log.info("Evento {} para el equipo {} sin suscriptos", event.eventId(), notifiedTeamId);
            dispatcherStatusService.recordProcessed(event, notifiedTeamId, 0);
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
                        notifiedTeamId,
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
            dispatcherStatusService.recordUndispatched(event, notifiedTeamId,
                    "Fallaron los " + failures + " callbacks de notificación");
        } else {
            dispatcherStatusService.recordProcessed(event, notifiedTeamId, notified);
        }
        log.info("Evento {} para el equipo {}: {} notificaciones creadas, {} fallidas",
                event.eventId(), notifiedTeamId, notified, failures);
    }

    private String buildMessage(MatchEventDTO event) {
        return switch (event.type()) {
            case "MATCH_START" -> "¡Arrancó el partido!";
            case "GOAL" -> "¡Gol! %s (%d') — marcador %d-%d".formatted(
                    event.playerName(), event.minute(), event.homeScore(), event.awayScore());
            case "CARD" -> "Tarjeta %s para %s (%d')".formatted(
                    "RED".equals(event.cardType()) ? "roja" : "amarilla", event.playerName(), event.minute());
            case "MATCH_END" -> "Final del partido — resultado %d-%d".formatted(
                    event.homeScore(), event.awayScore());
            default -> buildCustomMessage(event);
        };
    }

    // Tipo libre (ej. "PENAL", "TIRO LIBRE"): "Penal (33') — descripción".
    private String buildCustomMessage(MatchEventDTO event) {
        String tipo = event.type().toLowerCase(Locale.ROOT).replace('_', ' ');
        tipo = Character.toUpperCase(tipo.charAt(0)) + tipo.substring(1);
        String minuto = event.minute() != null ? " (" + event.minute() + "')" : "";
        boolean tieneDetalle = event.description() != null && !event.description().isBlank();
        return tipo + minuto + (tieneDetalle ? " — " + event.description() : "");
    }
}
