package com.tpnube.fan_identity_service.dto;

import java.time.Instant;

public record NotificationRequestDTO(
        String fanId,
        String eventId,
        String matchId,
        String teamId,
        String message,
        Instant occurredAt
) {}
