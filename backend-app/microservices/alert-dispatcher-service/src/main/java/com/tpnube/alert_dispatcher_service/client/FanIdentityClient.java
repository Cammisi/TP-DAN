package com.tpnube.alert_dispatcher_service.client;

import com.tpnube.alert_dispatcher_service.client.dto.FanSubscriberDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

// Cliente Feign hacia fan-identity-service, resuelto vía Eureka (spring-cloud-loadbalancer).
// Path interno (fuera de /api/**, contrato T-01 sección 2.5): el gateway no lo rutea,
// solo alcanzable dentro de la red de Docker.
@FeignClient(name = "fan-identity-service")
public interface FanIdentityClient {

    @GetMapping("/internal/fans/subscribers/{teamId}")
    List<FanSubscriberDTO> getSubscribers(@PathVariable("teamId") String teamId);
}
