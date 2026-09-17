package com.tpnube.fan_identity_service.controller;

import com.tpnube.fan_identity_service.dto.SubscriberDTO;
import com.tpnube.fan_identity_service.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Endpoints internos, fuera de /api/**: el gateway no los rutea, así que solo
// son alcanzables dentro de la red de Docker (D-3, contrato en T-01 sección 2.5).
@RestController
@RequestMapping("/internal/fans")
public class InternalFanController {

    private final SubscriptionService subscriptionService;

    public InternalFanController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/subscribers/{teamId}")
    public ResponseEntity<List<SubscriberDTO>> getSubscribers(@PathVariable String teamId) {
        List<SubscriberDTO> subscribers = subscriptionService.getSubscribers(teamId);
        return ResponseEntity.ok(subscribers);
    }
}
