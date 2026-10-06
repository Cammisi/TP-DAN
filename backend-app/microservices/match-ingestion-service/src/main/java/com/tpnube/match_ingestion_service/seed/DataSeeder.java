package com.tpnube.match_ingestion_service.seed;

import com.tpnube.match_ingestion_service.domain.CardType;
import com.tpnube.match_ingestion_service.domain.EventType;
import com.tpnube.match_ingestion_service.domain.Match;
import com.tpnube.match_ingestion_service.domain.MatchEvent;
import com.tpnube.match_ingestion_service.domain.MatchStatus;
import com.tpnube.match_ingestion_service.repository.MatchEventRepository;
import com.tpnube.match_ingestion_service.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;

    public DataSeeder(MatchRepository matchRepository, MatchEventRepository matchEventRepository) {
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedMatches();
        seedEvents();
        log.info("Datos semilla cargados correctamente");
    }

    private void seedMatches() {
        saveMatchIfAbsent("match-001", "team-colon", "team-union", MatchStatus.LIVE,
                Instant.parse("2026-09-26T19:00:00Z"), 0, 0);
        saveMatchIfAbsent("match-002", "team-river", "team-boca", MatchStatus.SCHEDULED,
                Instant.parse("2026-10-04T20:00:00Z"), null, null);
        saveMatchIfAbsent("match-003", "team-liverpool", "team-manchester-city", MatchStatus.FINISHED,
                Instant.parse("2026-09-20T14:00:00Z"), 1, 0);
    }

    private void seedEvents() {
        // Ids fijos para poder chequear la idempotencia con existsById.
        saveEventIfAbsent("seed-evt-001", "match-003", "team-liverpool", EventType.GOAL, 34,
                "Gol de Liverpool", Instant.parse("2026-09-20T14:34:00Z"),
                "Mohamed Salah", null, 1, 0);
        saveEventIfAbsent("seed-evt-002", "match-003", "team-manchester-city", EventType.CARD, 60,
                "Expulsión en Manchester City", Instant.parse("2026-09-20T15:00:00Z"),
                "Rodri", CardType.RED, null, null);
        saveEventIfAbsent("seed-evt-003", "match-003", null, EventType.MATCH_END, 90,
                "Fin del partido", Instant.parse("2026-09-20T15:30:00Z"),
                null, null, 1, 0);
    }

    // --- Helpers idempotentes ---

    private void saveMatchIfAbsent(String id, String homeTeamId, String awayTeamId, MatchStatus status,
                                    Instant scheduledAt, Integer homeScore, Integer awayScore) {
        if (matchRepository.existsById(id)) {
            return;
        }
        matchRepository.save(new Match(id, homeTeamId, awayTeamId, status, scheduledAt, homeScore, awayScore));
        log.info("Partido sembrado: {} ({} vs {})", id, homeTeamId, awayTeamId);
    }

    private void saveEventIfAbsent(String id, String matchId, String teamId, EventType type, Integer minute,
                                    String description, Instant occurredAt, String playerName,
                                    CardType cardType, Integer homeScore, Integer awayScore) {
        if (matchEventRepository.existsById(id)) {
            return;
        }
        MatchEvent event = new MatchEvent(id, matchId, teamId, type.name(), minute, description, occurredAt,
                playerName, cardType, homeScore, awayScore);
        matchEventRepository.save(event);
        log.info("Evento sembrado: {} ({})", id, type);
    }
}
