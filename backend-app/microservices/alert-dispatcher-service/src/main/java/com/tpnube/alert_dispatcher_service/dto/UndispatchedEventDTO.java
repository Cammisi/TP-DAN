package com.tpnube.alert_dispatcher_service.dto;

import java.time.Instant;

public record UndispatchedEventDTO(
        String eventId,
        String matchId,
        String teamId,
        Instant failedAt,
        String reason
) {}
