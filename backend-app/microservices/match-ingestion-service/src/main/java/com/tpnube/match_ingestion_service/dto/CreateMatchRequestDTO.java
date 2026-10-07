package com.tpnube.match_ingestion_service.dto;

import java.time.Instant;

public record CreateMatchRequestDTO(String homeTeamId, String awayTeamId, Instant scheduledAt) {}
