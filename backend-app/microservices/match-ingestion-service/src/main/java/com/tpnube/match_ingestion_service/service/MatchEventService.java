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
import java.util.Locale;

@Service
public class MatchEventService {

    private static final Logger log = LoggerFactory.getLogger(MatchEventService.class);
    private static final int MAX_CUSTOM_TYPE_LENGTH = 40;

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

        String type = normalizeType(request.type());
        EventType known = EventType.find(type);
        Integer minute = request.minute();
        if (minute == null || minute < 0) {
            throw new InvalidEventException("El minute es obligatorio y no puede ser negativo");
        }

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));

        MatchEvent event = known == null
                ? buildCustomEvent(match, type, request)
                : switch (known) {
                    case MATCH_START -> buildMatchStartEvent(match);
                    case GOAL -> buildGoalEvent(match, request);
                    case CARD -> buildCardEvent(match, request);
                    case MATCH_END -> buildMatchEndEvent(match, request);
                };
        event.setMinute(minute);
        event.setDescription(request.description());
        event.setOccurredAt(request.occurredAt() != null ? request.occurredAt() : Instant.now());

        matchEventRepository.save(event);
        applyToMatch(match, known, event);
        publish(match, event);

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
                .map(this::toDTO)
                .toList();
    }

    // Mayúsculas para que "goal" y "GOAL" sean el mismo tipo conocido.
    private String normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new InvalidEventException("El type es obligatorio");
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        if (type.length() > MAX_CUSTOM_TYPE_LENGTH) {
            throw new InvalidEventException("El type no puede superar los " + MAX_CUSTOM_TYPE_LENGTH + " caracteres");
        }
        return type;
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

    // Solo se inicia un partido que no empezó, así nadie recibe dos alertas de inicio.
    // Como el inicio es de los dos equipos, no se guarda un teamId.
    private MatchEvent buildMatchStartEvent(Match match) {
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw new InvalidEventException(
                    "El partido '" + match.getId() + "' no se puede iniciar: ya está " + match.getStatus());
        }

        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setType(EventType.MATCH_START.name());
        return event;
    }

    private MatchEvent buildGoalEvent(Match match, MatchEventDTO request) {
        String teamId = requireTeamInMatch(match, request.teamId());
        String playerName = requirePlayerName(request.playerName());
        int homeScore = requireNonNegative(request.homeScore(), "homeScore");
        int awayScore = requireNonNegative(request.awayScore(), "awayScore");

        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setTeamId(teamId);
        event.setType(EventType.GOAL.name());
        event.setPlayerName(playerName);
        event.setHomeScore(homeScore);
        event.setAwayScore(awayScore);
        return event;
    }

    // Tipo libre (penal, suspensión, etc.): necesita equipo, no cambia el marcador.
    private MatchEvent buildCustomEvent(Match match, String type, MatchEventDTO request) {
        String teamId = requireTeamInMatch(match, request.teamId());

        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setTeamId(teamId);
        event.setType(type);
        return event;
    }

    private MatchEvent buildCardEvent(Match match, MatchEventDTO request) {
        String teamId = requireTeamInMatch(match, request.teamId());
        String playerName = requirePlayerName(request.playerName());
        CardType cardType = parseCardType(request.cardType());

        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setTeamId(teamId);
        event.setType(EventType.CARD.name());
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
        event.setType(EventType.MATCH_END.name());
        event.setHomeScore(homeScore);
        event.setAwayScore(awayScore);
        return event;
    }

    // Cada evento deja el partido reflejando lo que acaba de pasar (marcador y status).
    // Un tipo libre (known == null) marca el partido en vivo pero no toca el marcador.
    private void applyToMatch(Match match, EventType known, MatchEvent event) {
        if (known == null) {
            match.setStatus(MatchStatus.LIVE);
        } else {
            switch (known) {
                case MATCH_START -> {
                    match.setHomeScore(0);
                    match.setAwayScore(0);
                    match.setStatus(MatchStatus.LIVE);
                }
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
        }
        matchRepository.save(match);
    }

    // Todo evento se publica una vez por equipo del partido, con ese equipo como key:
    // así les llega a los fans de los dos (contrato T-01 sección 5, D-7).
    private void publish(Match match, MatchEvent event) {
        MatchEventDTO message = toDTO(event);
        matchEventPublisher.publish(match.getHomeTeamId(), message);
        matchEventPublisher.publish(match.getAwayTeamId(), message);
    }

    private MatchEventDTO toDTO(MatchEvent event) {
        String cardType = event.getCardType() != null ? event.getCardType().name() : null;
        return new MatchEventDTO(
                event.getId(),
                event.getMatchId(),
                event.getTeamId(),
                event.getType(),
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
