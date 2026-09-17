package com.tpnube.fan_identity_service.dto;

import java.time.Instant;

public record NotificationItemDTO(
        String id,
        String message,
        String matchId,
        String teamId,
        String eventId,
        Instant createdAt
) {}
