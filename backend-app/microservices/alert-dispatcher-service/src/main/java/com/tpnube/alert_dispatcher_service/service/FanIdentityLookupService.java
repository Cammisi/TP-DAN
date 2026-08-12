package com.tpnube.alert_dispatcher_service.service;

import com.tpnube.alert_dispatcher_service.client.FanIdentityClient;
import com.tpnube.alert_dispatcher_service.client.dto.FanSubscriberDTO;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class FanIdentityLookupService {

    private static final String CIRCUIT_BREAKER_NAME = "fanIdentityService";

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
        // TODO: encolar el evento para reintentar cuando fan-identity-service se recupere,
        // en vez de simplemente devolver una lista vacía.
        return Collections.emptyList();
    }
}
