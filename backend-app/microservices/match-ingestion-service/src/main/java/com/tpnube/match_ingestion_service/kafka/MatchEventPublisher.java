package com.tpnube.match_ingestion_service.kafka;

import com.tpnube.match_ingestion_service.dto.MatchEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class MatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MatchEventPublisher.class);

    private final KafkaTemplate<String, MatchEventDTO> kafkaTemplate;
    private final String topic;

    public MatchEventPublisher(KafkaTemplate<String, MatchEventDTO> kafkaTemplate,
                                @Value("${topics.match-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    // El evento ya quedó persistido en Mongo antes de llamar acá: un fallo de
    // publicación se loguea pero nunca revierte la persistencia ni el POST.
    public void publish(String notifiedTeamId, MatchEventDTO event) {
        try {
            kafkaTemplate.send(topic, notifiedTeamId, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Fallo al publicar el evento {} para el equipo {} en Kafka",
                                    event.eventId(), notifiedTeamId, ex);
                        } else {
                            logSuccess(event, notifiedTeamId, result);
                        }
                    });
        } catch (Exception ex) {
            // Camino sincrónico: se superó max.block.ms.
            log.error("Fallo al publicar el evento {} para el equipo {} en Kafka",
                    event.eventId(), notifiedTeamId, ex);
        }
    }

    private void logSuccess(MatchEventDTO event, String notifiedTeamId, SendResult<String, MatchEventDTO> result) {
        log.info("Evento {} para el equipo {} publicado en {} partición {} offset {}",
                event.eventId(), notifiedTeamId, topic,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }
}
