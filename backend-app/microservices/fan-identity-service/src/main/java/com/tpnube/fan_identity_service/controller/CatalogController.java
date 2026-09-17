package com.tpnube.fan_identity_service.controller;

import com.tpnube.fan_identity_service.dto.LeagueDTO;
import com.tpnube.fan_identity_service.dto.TeamCatalogDTO;
import com.tpnube.fan_identity_service.service.CatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/leagues")
    public ResponseEntity<List<LeagueDTO>> getLeagues() {
        return ResponseEntity.ok(catalogService.getLeagues());
    }

    @GetMapping("/teams")
    public ResponseEntity<List<TeamCatalogDTO>> searchTeams(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String leagueId) {
        return ResponseEntity.ok(catalogService.searchTeams(name, leagueId));
    }
}
