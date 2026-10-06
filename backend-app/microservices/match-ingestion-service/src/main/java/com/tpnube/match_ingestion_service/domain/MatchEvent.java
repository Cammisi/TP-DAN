package com.tpnube.match_ingestion_service.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Un único documento para los tres tipos de evento: los campos que no aplican
// a un tipo quedan en null y Spring Data Mongo no los persiste.
@Document(collection = "match_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchEvent {

    @Id
    private String id;

    private String matchId;

    // Null en MATCH_END: el fin del partido es de los dos equipos.
    private String teamId;

    // Un EventType conocido (GOAL, CARD, MATCH_END) o un texto libre (ej. PENAL).
    private String type;
    private Integer minute;
    private String description;
    private Instant occurredAt;

    // Solo GOAL y CARD
    private String playerName;

    // Solo CARD
    private CardType cardType;

    // Solo GOAL y MATCH_END
    private Integer homeScore;
    private Integer awayScore;
}
