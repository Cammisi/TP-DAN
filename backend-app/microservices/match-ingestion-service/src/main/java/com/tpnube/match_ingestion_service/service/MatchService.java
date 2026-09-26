package com.tpnube.match_ingestion_service.service;

import com.tpnube.match_ingestion_service.domain.Match;
import com.tpnube.match_ingestion_service.dto.MatchDTO;
import com.tpnube.match_ingestion_service.repository.MatchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MatchService {

    private final MatchRepository matchRepository;

    public MatchService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    public List<MatchDTO> getMatches(String teamId) {
        List<Match> matches = (teamId == null || teamId.isBlank())
                ? matchRepository.findAllByOrderByScheduledAtAsc()
                : matchRepository.findByHomeTeamIdOrAwayTeamIdOrderByScheduledAtAsc(teamId, teamId);

        return matches.stream()
                .map(m -> new MatchDTO(
                        m.getId(),
                        m.getHomeTeamId(),
                        m.getAwayTeamId(),
                        m.getStatus().name(),
                        m.getScheduledAt(),
                        m.getHomeScore(),
                        m.getAwayScore()
                ))
                .toList();
    }
}
