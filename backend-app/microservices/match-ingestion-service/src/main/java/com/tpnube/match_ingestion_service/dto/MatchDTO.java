package com.tpnube.match_ingestion_service.dto;

import java.time.Instant;

public record MatchDTO(
        String id,
        String homeTeamId,
        String awayTeamId,
        String status,
        Instant scheduledAt,
        Integer homeScore,
        Integer awayScore
) {}
