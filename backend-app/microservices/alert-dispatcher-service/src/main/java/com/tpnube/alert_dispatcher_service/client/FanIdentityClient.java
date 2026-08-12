package com.tpnube.alert_dispatcher_service.client;

import com.tpnube.alert_dispatcher_service.client.dto.FanSubscriberDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

// Cliente Feign hacia fan-identity-service, resuelto vía Eureka (spring-cloud-loadbalancer).
// El path del endpoint es provisorio: hay que alinearlo con lo que finalmente exponga fan-identity-service.
@FeignClient(name = "fan-identity-service")
public interface FanIdentityClient {

    @GetMapping("/api/fans/subscribers/{teamId}")
    List<FanSubscriberDTO> getSubscribers(@PathVariable("teamId") String teamId);
}
