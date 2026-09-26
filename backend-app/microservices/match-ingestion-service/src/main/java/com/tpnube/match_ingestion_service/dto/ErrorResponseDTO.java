package com.tpnube.match_ingestion_service.dto;

import java.time.Instant;

public record ErrorResponseDTO(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path
) {
}
