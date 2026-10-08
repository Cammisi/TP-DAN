package com.tpnube.match_ingestion_service.dto;

import java.time.Instant;

// Mismo shape para todos los tipos de evento (contrato T-01 sección 3.1).
public record MatchEventDTO(
        String eventId,
        String matchId,
        String teamId,
        String type,
        Integer minute,
        String description,
        String playerName,
        String cardType,
        Integer homeScore,
        Integer awayScore,
        Instant occurredAt
) {}
