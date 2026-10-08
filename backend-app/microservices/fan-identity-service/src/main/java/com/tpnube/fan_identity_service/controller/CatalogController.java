package com.tpnube.fan_identity_service.controller;

import com.tpnube.fan_identity_service.dto.CreateLeagueRequestDTO;
import com.tpnube.fan_identity_service.dto.CreateTeamRequestDTO;
import com.tpnube.fan_identity_service.dto.LeagueDTO;
import com.tpnube.fan_identity_service.dto.TeamCatalogDTO;
import com.tpnube.fan_identity_service.service.CatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // Admin-only: lo exige el gateway (cualquier no-GET bajo /api/catalog/**, ver JwtValidationFilter).
    @PostMapping("/leagues")
    public ResponseEntity<LeagueDTO> createLeague(@RequestBody CreateLeagueRequestDTO request) {
        CatalogService.LeagueResult result = catalogService.createLeague(request.name());
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.league());
    }

    @GetMapping("/teams")
    public ResponseEntity<List<TeamCatalogDTO>> searchTeams(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String leagueId) {
        return ResponseEntity.ok(catalogService.searchTeams(name, leagueId));
    }

    // Admin-only: lo exige el gateway (cualquier no-GET bajo /api/catalog/**, ver JwtValidationFilter).
    @PostMapping("/teams")
    public ResponseEntity<TeamCatalogDTO> createTeam(@RequestBody CreateTeamRequestDTO request) {
        CatalogService.TeamResult result = catalogService.createTeam(request.name(), request.leagueId());
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.team());
    }
}
