package com.tpnube.alert_dispatcher_service.dto;

import java.util.List;

public record DispatcherStatusDTO(
        String circuitState,
        List<ProcessedEventDTO> lastProcessedEvents,
        List<UndispatchedEventDTO> undispatchedEvents
) {}
