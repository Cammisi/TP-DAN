package com.tpnube.alert_dispatcher_service.service;

import com.tpnube.alert_dispatcher_service.dto.DispatcherStatusDTO;
import com.tpnube.alert_dispatcher_service.dto.MatchEventDTO;
import com.tpnube.alert_dispatcher_service.dto.ProcessedEventDTO;
import com.tpnube.alert_dispatcher_service.dto.UndispatchedEventDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;

// Registro en memoria (D-6): se pierde al reiniciar el servicio (ver explicacion_circuit_breaker.md).
@Service
public class DispatcherStatusService {

    private static final int MAX_HISTORY = 20;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final LinkedList<ProcessedEventDTO> lastProcessedEvents = new LinkedList<>();
    private final LinkedList<UndispatchedEventDTO> undispatchedEvents = new LinkedList<>();

    public DispatcherStatusService(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public synchronized void recordProcessed(MatchEventDTO event, String notifiedTeamId, int subscriberCount) {
        addBounded(lastProcessedEvents, new ProcessedEventDTO(
                event.eventId(), event.matchId(), notifiedTeamId, Instant.now(), subscriberCount));
    }

    public synchronized void recordUndispatched(MatchEventDTO event, String notifiedTeamId, String reason) {
        addBounded(undispatchedEvents, new UndispatchedEventDTO(
                event.eventId(), event.matchId(), notifiedTeamId, Instant.now(), reason));
    }

    public synchronized DispatcherStatusDTO getStatus() {
        return new DispatcherStatusDTO(
                currentCircuitState(),
                new ArrayList<>(lastProcessedEvents),
                new ArrayList<>(undispatchedEvents)
        );
    }

    private <T> void addBounded(LinkedList<T> list, T item) {
        list.addFirst(item);
        while (list.size() > MAX_HISTORY) {
            list.removeLast();
        }
    }

    private String currentCircuitState() {
        // find(...) y no circuitBreaker(...): este último crea el circuito con config
        // default si no existe (ver gotcha en CLAUDE.md).
        return circuitBreakerRegistry.find(FanIdentityLookupService.CIRCUIT_BREAKER_NAME)
                .map(cb -> cb.getState().name())
                .orElse(CircuitBreaker.State.CLOSED.name());
    }
}
