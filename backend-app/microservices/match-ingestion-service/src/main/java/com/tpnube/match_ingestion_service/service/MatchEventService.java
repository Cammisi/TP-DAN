package com.tpnube.match_ingestion_service.service;

import com.tpnube.match_ingestion_service.domain.CardType;
import com.tpnube.match_ingestion_service.domain.EventType;
import com.tpnube.match_ingestion_service.domain.Match;
import com.tpnube.match_ingestion_service.domain.MatchEvent;
import com.tpnube.match_ingestion_service.domain.MatchStatus;
import com.tpnube.match_ingestion_service.dto.MatchEventDTO;
import com.tpnube.match_ingestion_service.kafka.MatchEventPublisher;
import com.tpnube.match_ingestion_service.repository.MatchEventRepository;
import com.tpnube.match_ingestion_service.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class MatchEventService {

    private static final Logger log = LoggerFactory.getLogger(MatchEventService.class);

    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchEventPublisher matchEventPublisher;

    public MatchEventService(MatchRepository matchRepository,
                              MatchEventRepository matchEventRepository,
                              MatchEventPublisher matchEventPublisher) {
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchEventPublisher = matchEventPublisher;
    }

    public String ingestEvent(String matchId, MatchEventDTO request) {
        if (request.matchId() != null && !request.matchId().equals(matchId)) {
            throw new InvalidEventException("El matchId del body no coincide con el de la URL");
        }

        EventType type = parseType(request.type());
        Integer minute = request.minute();
        if (minute == null || minute < 0) {
            throw new InvalidEventException("El minute es obligatorio y no puede ser negativo");
        }

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));

        MatchEvent event = switch (type) {
            case GOAL -> buildGoalEvent(match, request);
            case CARD -> buildCardEvent(match, request);
            case MATCH_END -> buildMatchEndEvent(match, request);
        };
        event.setMinute(minute);
        event.setDescription(request.description());
        event.setOccurredAt(request.occurredAt() != null ? request.occurredAt() : Instant.now());

        matchEventRepository.save(event);
        applyToMatch(match, type, event);
        publish(match, type, event);

        log.info("Evento ingestado exitosamente: eventId={}, matchId={}, teamId={}, type={}",
                event.getId(), match.getId(), event.getTeamId(), type);

        return event.getId();
    }

    public List<MatchEventDTO> getEvents(String matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        return matchEventRepository.findAllByMatchIdOrderByOccurredAtAsc(matchId)
                .stream()
                .map(e -> toDTO(e, e.getTeamId()))
                .toList();
    }

    private EventType parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new InvalidEventException("El type es obligatorio");
        }
        try {
            return EventType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("El type debe ser GOAL, CARD o MATCH_END");
        }
    }

    private CardType parseCardType(String rawCardType) {
        if (rawCardType == null || rawCardType.isBlank()) {
            throw new InvalidEventException("El cardType es obligatorio en un evento CARD");
        }
        try {
            return CardType.valueOf(rawCardType);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("El cardType debe ser YELLOW o RED");
        }
    }

    private String requireTeamInMatch(Match match, String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new InvalidEventException("El teamId es obligatorio");
        }
        if (!teamId.equals(match.getHomeTeamId()) && !teamId.equals(match.getAwayTeamId())) {
            throw new InvalidEventException("El equipo '" + teamId + "' no juega el partido '" + match.getId() + "'");
        }
        return teamId;
    }

    private String requirePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new InvalidEventException("El playerName es obligatorio");
        }
        return playerName;
    }

    private int requireNonNegative(Integer value, String field) {
        if (value == null || value < 0) {
            throw new InvalidEventException("El " + field + " es obligatorio y no puede ser negativo");
        }
        return value;
    }

    private MatchEvent buildGoalEvent(Match match, MatchEventDTO request) {
        String teamId = requireTeamInMatch(match, request.teamId());
        String playerName = requirePlayerName(request.playerName());
        int homeScore = requireNonNegative(request.homeScore(), "homeScore");
        int awayScore = requireNonNegative(request.awayScore(), "awayScore");

        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setTeamId(teamId);
        event.setType(EventType.GOAL);
        event.setPlayerName(playerName);
        event.setHomeScore(homeScore);
        event.setAwayScore(awayScore);
        return event;
    }

    private MatchEvent buildCardEvent(Match match, MatchEventDTO request) {
        String teamId = requireTeamInMatch(match, request.teamId());
        String playerName = requirePlayerName(request.playerName());
        CardType cardType = parseCardType(request.cardType());

        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setTeamId(teamId);
        event.setType(EventType.CARD);
        event.setPlayerName(playerName);
        event.setCardType(cardType);
        return event;
    }

    private MatchEvent buildMatchEndEvent(Match match, MatchEventDTO request) {
        // El fin del partido es de los dos equipos: no se guarda un teamId único.
        int homeScore = requireNonNegative(request.homeScore(), "homeScore");
        int awayScore = requireNonNegative(request.awayScore(), "awayScore");

        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setType(EventType.MATCH_END);
        event.setHomeScore(homeScore);
        event.setAwayScore(awayScore);
        return event;
    }

    // Cada evento deja el partido reflejando lo que acaba de pasar (marcador y status).
    private void applyToMatch(Match match, EventType type, MatchEvent event) {
        switch (type) {
            case GOAL -> {
                match.setHomeScore(event.getHomeScore());
                match.setAwayScore(event.getAwayScore());
                match.setStatus(MatchStatus.LIVE);
            }
            case CARD -> match.setStatus(MatchStatus.LIVE);
            case MATCH_END -> {
                match.setHomeScore(event.getHomeScore());
                match.setAwayScore(event.getAwayScore());
                match.setStatus(MatchStatus.FINISHED);
            }
        }
        matchRepository.save(match);
    }

    private void publish(Match match, EventType type, MatchEvent event) {
        if (type == EventType.MATCH_END) {
            // Un mensaje por equipo participante, mismo eventId (contrato T-01 sección 5).
            matchEventPublisher.publish(toDTO(event, match.getHomeTeamId()));
            matchEventPublisher.publish(toDTO(event, match.getAwayTeamId()));
        } else {
            matchEventPublisher.publish(toDTO(event, event.getTeamId()));
        }
    }

    private MatchEventDTO toDTO(MatchEvent event, String teamId) {
        String cardType = event.getCardType() != null ? event.getCardType().name() : null;
        return new MatchEventDTO(
                event.getId(),
                event.getMatchId(),
                teamId,
                event.getType().name(),
                event.getMinute(),
                event.getDescription(),
                event.getPlayerName(),
                cardType,
                event.getHomeScore(),
                event.getAwayScore(),
                event.getOccurredAt()
        );
    }

    public static class InvalidEventException extends RuntimeException {
        public InvalidEventException(String message) {
            super(message);
        }
    }

    public static class MatchNotFoundException extends RuntimeException {
        public MatchNotFoundException(String matchId) {
            super("El partido '" + matchId + "' no existe");
        }
    }
}
