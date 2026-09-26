package com.tpnube.alert_dispatcher_service.dto;

import java.time.Instant;

// Copia del MatchEventDTO de match-ingestion-service (contrato T-01 sección 3.1).
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
