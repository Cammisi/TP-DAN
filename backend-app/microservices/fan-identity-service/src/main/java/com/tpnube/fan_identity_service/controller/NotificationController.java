package com.tpnube.fan_identity_service.controller;

import com.tpnube.fan_identity_service.dto.NotificationItemDTO;
import com.tpnube.fan_identity_service.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fans/me/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationItemDTO>> getNotifications(
            @AuthenticationPrincipal String principal) {
        Long fanId = Long.parseLong(principal);
        List<NotificationItemDTO> notifications = notificationService.getNotifications(fanId);
        return ResponseEntity.ok(notifications);
    }
}
