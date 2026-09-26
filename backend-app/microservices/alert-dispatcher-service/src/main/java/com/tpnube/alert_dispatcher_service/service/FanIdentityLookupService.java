package com.tpnube.alert_dispatcher_service.service;

import com.tpnube.alert_dispatcher_service.client.FanIdentityClient;
import com.tpnube.alert_dispatcher_service.client.dto.FanSubscriberDTO;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FanIdentityLookupService {

    // public: DispatcherStatusService consulta este mismo circuito por nombre.
    public static final String CIRCUIT_BREAKER_NAME = "fanIdentityService";

    private static final Logger log = LoggerFactory.getLogger(FanIdentityLookupService.class);

    private final FanIdentityClient fanIdentityClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public FanIdentityLookupService(FanIdentityClient fanIdentityClient,
                                     CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.fanIdentityClient = fanIdentityClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public List<FanSubscriberDTO> getSubscribers(String teamId) {
        return circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                .run(() -> fanIdentityClient.getSubscribers(teamId),
                        throwable -> fallbackSubscribers(teamId, throwable));
    }

    private List<FanSubscriberDTO> fallbackSubscribers(String teamId, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.warn("Circuito abierto: no se consultó fan-identity para el equipo {}", teamId);
        } else {
            log.warn("Falló la consulta de suscriptores del equipo {}: {}", teamId, throwable.getMessage());
        }
        // Se propaga en vez de devolver [] para no confundir "sin suscriptos" con "no pude averiguarlo".
        throw new SubscribersUnavailableException(teamId, throwable);
    }

    public static class SubscribersUnavailableException extends RuntimeException {
        public SubscribersUnavailableException(String teamId, Throwable cause) {
            super("No se pudieron obtener los suscriptores del equipo '" + teamId + "'", cause);
        }
    }
}
