package com.tpnube.match_ingestion_service.service;

import com.tpnube.match_ingestion_service.domain.Match;
import com.tpnube.match_ingestion_service.domain.MatchStatus;
import com.tpnube.match_ingestion_service.dto.CreateMatchRequestDTO;
import com.tpnube.match_ingestion_service.dto.MatchDTO;
import com.tpnube.match_ingestion_service.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final MatchRepository matchRepository;

    public MatchService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    public List<MatchDTO> getMatches(String teamId) {
        List<Match> matches = (teamId == null || teamId.isBlank())
                ? matchRepository.findAllByOrderByScheduledAtAsc()
                : matchRepository.findByHomeTeamIdOrAwayTeamIdOrderByScheduledAtAsc(teamId, teamId);

        return matches.stream().map(this::toDTO).toList();
    }

    public MatchDTO createMatch(CreateMatchRequestDTO request) {
        String homeTeamId = requireTeamId(request.homeTeamId(), "homeTeamId");
        String awayTeamId = requireTeamId(request.awayTeamId(), "awayTeamId");
        if (homeTeamId.equals(awayTeamId)) {
            throw new InvalidMatchException("homeTeamId y awayTeamId no pueden ser el mismo equipo");
        }
        // id null: Mongo le asigna un ObjectId, igual que ya hace MatchEvent para eventos que no son semilla.
        Instant scheduledAt = request.scheduledAt() != null ? request.scheduledAt() : Instant.now();
        Match match = new Match(null, homeTeamId, awayTeamId, MatchStatus.SCHEDULED, scheduledAt, null, null);
        matchRepository.save(match);

        log.info("Partido creado: id={}, {} vs {}", match.getId(), homeTeamId, awayTeamId);
        return toDTO(match);
    }

    private String requireTeamId(String teamId, String field) {
        if (teamId == null || teamId.isBlank()) {
            throw new InvalidMatchException("El " + field + " es obligatorio");
        }
        return teamId;
    }

    private MatchDTO toDTO(Match m) {
        return new MatchDTO(
                m.getId(),
                m.getHomeTeamId(),
                m.getAwayTeamId(),
                m.getStatus().name(),
                m.getScheduledAt(),
                m.getHomeScore(),
                m.getAwayScore()
        );
    }

    public static class InvalidMatchException extends RuntimeException {
        public InvalidMatchException(String message) {
            super(message);
        }
    }
}
