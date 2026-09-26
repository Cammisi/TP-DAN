package com.tpnube.match_ingestion_service.controller;

import com.tpnube.match_ingestion_service.dto.EventCreatedDTO;
import com.tpnube.match_ingestion_service.dto.MatchDTO;
import com.tpnube.match_ingestion_service.dto.MatchEventDTO;
import com.tpnube.match_ingestion_service.service.MatchEventService;
import com.tpnube.match_ingestion_service.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;
    private final MatchEventService matchEventService;

    public MatchController(MatchService matchService, MatchEventService matchEventService) {
        this.matchService = matchService;
        this.matchEventService = matchEventService;
    }

    @GetMapping
    public ResponseEntity<List<MatchDTO>> getMatches(@RequestParam(required = false) String teamId) {
        return ResponseEntity.ok(matchService.getMatches(teamId));
    }

    @GetMapping("/{matchId}/events")
    public ResponseEntity<List<MatchEventDTO>> getEvents(@PathVariable String matchId) {
        return ResponseEntity.ok(matchEventService.getEvents(matchId));
    }

    @PostMapping("/{matchId}/events")
    public ResponseEntity<EventCreatedDTO> ingestEvent(@PathVariable String matchId,
                                                         @RequestBody MatchEventDTO request) {
        String eventId = matchEventService.ingestEvent(matchId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new EventCreatedDTO(eventId));
    }
}
