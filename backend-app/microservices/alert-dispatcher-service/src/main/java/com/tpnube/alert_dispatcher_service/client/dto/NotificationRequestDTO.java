package com.tpnube.alert_dispatcher_service.client.dto;

import java.time.Instant;

public record NotificationRequestDTO(
        String fanId,
        String eventId,
        String matchId,
        String teamId,
        String message,
        Instant occurredAt
) {}
