package com.tpnube.alert_dispatcher_service.listener;

import com.tpnube.alert_dispatcher_service.dto.MatchEventDTO;
import com.tpnube.alert_dispatcher_service.service.AlertDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(MatchEventListener.class);

    private final AlertDispatchService alertDispatchService;

    public MatchEventListener(AlertDispatchService alertDispatchService) {
        this.alertDispatchService = alertDispatchService;
    }

    @KafkaListener(topics = "${topics.match-events}")
    public void onMatchEvent(MatchEventDTO event) {
        log.info("Evento recibido: {} ({}) del equipo {}", event.eventId(), event.type(), event.teamId());
        alertDispatchService.dispatch(event);
    }
}
