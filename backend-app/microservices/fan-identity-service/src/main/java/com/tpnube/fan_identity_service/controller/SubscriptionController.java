package com.tpnube.fan_identity_service.controller;

import com.tpnube.fan_identity_service.dto.SubscribeRequestDTO;
import com.tpnube.fan_identity_service.dto.SubscriptionItemDTO;
import com.tpnube.fan_identity_service.dto.SubscriptionResponseDTO;
import com.tpnube.fan_identity_service.service.SubscriptionService;
import com.tpnube.fan_identity_service.service.SubscriptionService.SubscriptionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fans/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponseDTO> subscribe(
            @AuthenticationPrincipal String principal,
            @RequestBody SubscribeRequestDTO request) {
        Long fanId = Long.parseLong(principal);
        SubscriptionResult result = subscriptionService.subscribe(fanId, request.teamId());

        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal String principal,
            @PathVariable String teamId) {
        Long fanId = Long.parseLong(principal);
        subscriptionService.unsubscribe(fanId, teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionItemDTO>> getSubscriptions(
            @AuthenticationPrincipal String principal) {
        Long fanId = Long.parseLong(principal);
        List<SubscriptionItemDTO> subscriptions = subscriptionService.getSubscriptions(fanId);
        return ResponseEntity.ok(subscriptions);
    }
}
