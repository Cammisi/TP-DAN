package com.tpnube.match_ingestion_service.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    private String id;

    private String homeTeamId;
    private String awayTeamId;

    private MatchStatus status;
    private Instant scheduledAt;

    // Integer y no int: el contrato admite null mientras el partido no arrancó.
    private Integer homeScore;
    private Integer awayScore;
}
