package com.tpnube.alert_dispatcher_service.client;

import com.tpnube.alert_dispatcher_service.client.dto.FanSubscriberDTO;
import com.tpnube.alert_dispatcher_service.client.dto.NotificationCreatedDTO;
import com.tpnube.alert_dispatcher_service.client.dto.NotificationRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

// Cliente Feign hacia fan-identity-service, resuelto vía Eureka (spring-cloud-loadbalancer).
// Paths internos (fuera de /api/**, contrato T-01 secciones 2.3 y 2.5): el gateway no los
// rutea, solo alcanzables dentro de la red de Docker.
@FeignClient(name = "fan-identity-service")
public interface FanIdentityClient {

    @GetMapping("/internal/fans/subscribers/{teamId}")
    List<FanSubscriberDTO> getSubscribers(@PathVariable("teamId") String teamId);

    @PostMapping("/internal/fans/notifications")
    NotificationCreatedDTO createNotification(@RequestBody NotificationRequestDTO request);
}
