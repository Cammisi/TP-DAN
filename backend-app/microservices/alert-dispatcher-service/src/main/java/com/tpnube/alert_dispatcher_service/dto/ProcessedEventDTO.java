package com.tpnube.alert_dispatcher_service.dto;

import java.time.Instant;

public record ProcessedEventDTO(
        String eventId,
        String matchId,
        String teamId,
        Instant dispatchedAt,
        int subscriberCount
) {}
