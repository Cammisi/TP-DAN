package com.tpnube.match_ingestion_service.service;

import com.tpnube.match_ingestion_service.domain.Match;
import com.tpnube.match_ingestion_service.domain.MatchEvent;
import com.tpnube.match_ingestion_service.domain.MatchStatus;
import com.tpnube.match_ingestion_service.dto.MatchEventDTO;
import com.tpnube.match_ingestion_service.kafka.MatchEventPublisher;
import com.tpnube.match_ingestion_service.repository.MatchEventRepository;
import com.tpnube.match_ingestion_service.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchEventServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchEventRepository matchEventRepository;

    @Mock
    private MatchEventPublisher matchEventPublisher;

    private MatchEventService matchEventService;

    @BeforeEach
    void setUp() {
        matchEventService = new MatchEventService(matchRepository, matchEventRepository, matchEventPublisher);
    }

    @Test
    void rechazaUnEventoSinType() {
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", "team-colon", null, 10, "desc", "Jugador", null, 1, 0, Instant.now());

        assertThatThrownBy(() -> matchEventService.ingestEvent("match-001", request))
                .isInstanceOf(MatchEventService.InvalidEventException.class);
        verify(matchEventRepository, never()).save(any());
    }

    @Test
    void rechazaUnEventoSinMinute() {
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", "team-colon", "GOAL", null, "desc", "Jugador", null, 1, 0, Instant.now());

        assertThatThrownBy(() -> matchEventService.ingestEvent("match-001", request))
                .isInstanceOf(MatchEventService.InvalidEventException.class);
        verify(matchEventRepository, never()).save(any());
    }

    @Test
    void rechazaUnEventoDeUnPartidoInexistente() {
        when(matchRepository.findById("match-999")).thenReturn(Optional.empty());
        MatchEventDTO request = new MatchEventDTO(
                null, "match-999", "team-colon", "GOAL", 10, "desc", "Jugador", null, 1, 0, Instant.now());

        assertThatThrownBy(() -> matchEventService.ingestEvent("match-999", request))
                .isInstanceOf(MatchEventService.MatchNotFoundException.class);
        verify(matchEventRepository, never()).save(any());
    }

    @Test
    void rechazaUnGolSinJugador() {
        Match match = new Match("match-001", "team-colon", "team-union", MatchStatus.LIVE, Instant.now(), 0, 0);
        when(matchRepository.findById("match-001")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", "team-colon", "GOAL", 10, "desc", null, null, 1, 0, Instant.now());

        assertThatThrownBy(() -> matchEventService.ingestEvent("match-001", request))
                .isInstanceOf(MatchEventService.InvalidEventException.class);
        verify(matchEventRepository, never()).save(any());
    }

    @Test
    void unFinDePartidoSeGuardaYPublicaConElMatchId() {
        Match match = new Match("match-001", "team-colon", "team-union", MatchStatus.LIVE, Instant.now(), 1, 0);
        when(matchRepository.findById("match-001")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", null, "MATCH_END", 90, "Final", null, null, 1, 0, Instant.now());

        matchEventService.ingestEvent("match-001", request);

        ArgumentCaptor<MatchEvent> saved = ArgumentCaptor.forClass(MatchEvent.class);
        verify(matchEventRepository).save(saved.capture());
        assertThat(saved.getValue().getMatchId()).isEqualTo("match-001");

        ArgumentCaptor<String> notifiedTeams = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MatchEventDTO> published = ArgumentCaptor.forClass(MatchEventDTO.class);
        verify(matchEventPublisher, times(2)).publish(notifiedTeams.capture(), published.capture());
        assertThat(notifiedTeams.getAllValues()).containsExactly("team-colon", "team-union");
        assertThat(published.getAllValues())
                .extracting(MatchEventDTO::matchId)
                .containsOnly("match-001");
        assertThat(published.getAllValues())
                .extracting(MatchEventDTO::teamId)
                .containsOnlyNulls();
    }

    @Test
    void unGolSePublicaParaLosFansDeLosDosEquiposConElEquipoQueLoHizo() {
        Match match = new Match("match-001", "team-colon", "team-union", MatchStatus.LIVE, Instant.now(), 0, 0);
        when(matchRepository.findById("match-001")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", "team-union", "GOAL", 10, "desc", "Jugador", null, 0, 1, Instant.now());

        matchEventService.ingestEvent("match-001", request);

        ArgumentCaptor<String> notifiedTeams = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MatchEventDTO> published = ArgumentCaptor.forClass(MatchEventDTO.class);
        verify(matchEventPublisher, times(2)).publish(notifiedTeams.capture(), published.capture());
        assertThat(notifiedTeams.getAllValues()).containsExactly("team-colon", "team-union");
        assertThat(published.getAllValues())
                .extracting(MatchEventDTO::teamId)
                .containsOnly("team-union");
    }

    @Test
    void unGolEnUnPartidoProgramadoLoPasaALive() {
        Match match = new Match("match-002", "team-river", "team-boca", MatchStatus.SCHEDULED, Instant.now(), null, null);
        when(matchRepository.findById("match-002")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-002", "team-boca", "GOAL", 5, "desc", "Jugador", null, 0, 1, Instant.now());

        matchEventService.ingestEvent("match-002", request);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.LIVE);
        assertThat(match.getAwayScore()).isEqualTo(1);
    }

    @Test
    void unInicioDePartidoLoPasaALiveConMarcadorCeroACero() {
        Match match = new Match("match-002", "team-river", "team-boca", MatchStatus.SCHEDULED, Instant.now(), null, null);
        when(matchRepository.findById("match-002")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-002", null, "MATCH_START", 0, null, null, null, null, null, Instant.now());

        matchEventService.ingestEvent("match-002", request);

        ArgumentCaptor<MatchEvent> saved = ArgumentCaptor.forClass(MatchEvent.class);
        verify(matchEventRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("MATCH_START");
        assertThat(saved.getValue().getTeamId()).isNull();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.LIVE);
        assertThat(match.getHomeScore()).isEqualTo(0);
        assertThat(match.getAwayScore()).isEqualTo(0);
        verify(matchEventPublisher).publish(eq("team-river"), any());
        verify(matchEventPublisher).publish(eq("team-boca"), any());
    }

    @Test
    void unInicioDePartidoSeRechazaSiElPartidoYaEmpezo() {
        Match match = new Match("match-001", "team-colon", "team-union", MatchStatus.LIVE, Instant.now(), 1, 0);
        when(matchRepository.findById("match-001")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", null, "MATCH_START", 0, null, null, null, null, null, Instant.now());

        assertThatThrownBy(() -> matchEventService.ingestEvent("match-001", request))
                .isInstanceOf(MatchEventService.InvalidEventException.class);
        verify(matchEventRepository, never()).save(any());
        verify(matchEventPublisher, never()).publish(any(), any());
    }

    @Test
    void unInicioDePartidoSeRechazaSiElPartidoYaTermino() {
        Match match = new Match("match-003", "team-liverpool", "team-city", MatchStatus.FINISHED, Instant.now(), 1, 0);
        when(matchRepository.findById("match-003")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-003", null, "MATCH_START", 0, null, null, null, null, null, Instant.now());

        assertThatThrownBy(() -> matchEventService.ingestEvent("match-003", request))
                .isInstanceOf(MatchEventService.InvalidEventException.class);
        verify(matchEventRepository, never()).save(any());
    }

    @Test
    void unTipoLibreSeGuardaNormalizadoYNoCambiaElMarcador() {
        Match match = new Match("match-001", "team-colon", "team-union", MatchStatus.LIVE, Instant.now(), 1, 0);
        when(matchRepository.findById("match-001")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", "team-colon", " penal ", 33, "Lo cobró Colón", null, null, null, null, Instant.now());

        matchEventService.ingestEvent("match-001", request);

        ArgumentCaptor<MatchEvent> saved = ArgumentCaptor.forClass(MatchEvent.class);
        verify(matchEventRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("PENAL");
        assertThat(saved.getValue().getTeamId()).isEqualTo("team-colon");
        assertThat(match.getHomeScore()).isEqualTo(1);
        assertThat(match.getAwayScore()).isEqualTo(0);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.LIVE);
    }

    @Test
    void unTipoConocidoEscritoEnMinusculasSeManejaComoSuTipo() {
        Match match = new Match("match-001", "team-colon", "team-union", MatchStatus.LIVE, Instant.now(), 0, 0);
        when(matchRepository.findById("match-001")).thenReturn(Optional.of(match));
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", "team-colon", "goal", 10, "desc", "Jugador", null, 1, 0, Instant.now());

        matchEventService.ingestEvent("match-001", request);

        ArgumentCaptor<MatchEvent> saved = ArgumentCaptor.forClass(MatchEvent.class);
        verify(matchEventRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("GOAL");
        assertThat(match.getHomeScore()).isEqualTo(1);
    }

    @Test
    void unTipoLibreDemasiadoLargoSeRechaza() {
        MatchEventDTO request = new MatchEventDTO(
                null, "match-001", "team-colon", "x".repeat(41), 10, null, null, null, null, null, Instant.now());

        assertThatThrownBy(() -> matchEventService.ingestEvent("match-001", request))
                .isInstanceOf(MatchEventService.InvalidEventException.class);
        verify(matchEventRepository, never()).save(any());
    }
}
