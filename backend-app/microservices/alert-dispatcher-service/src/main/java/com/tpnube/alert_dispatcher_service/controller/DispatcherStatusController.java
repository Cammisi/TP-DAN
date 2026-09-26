package com.tpnube.alert_dispatcher_service.controller;

import com.tpnube.alert_dispatcher_service.dto.DispatcherStatusDTO;
import com.tpnube.alert_dispatcher_service.service.DispatcherStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class DispatcherStatusController {

    private final DispatcherStatusService dispatcherStatusService;

    public DispatcherStatusController(DispatcherStatusService dispatcherStatusService) {
        this.dispatcherStatusService = dispatcherStatusService;
    }

    @GetMapping("/status")
    public ResponseEntity<DispatcherStatusDTO> getStatus() {
        return ResponseEntity.ok(dispatcherStatusService.getStatus());
    }
}
