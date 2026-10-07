package com.tpnube.match_ingestion_service.service;

import com.tpnube.match_ingestion_service.domain.Match;
import com.tpnube.match_ingestion_service.domain.MatchStatus;
import com.tpnube.match_ingestion_service.dto.CreateMatchRequestDTO;
import com.tpnube.match_ingestion_service.dto.MatchDTO;
import com.tpnube.match_ingestion_service.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    private MatchService matchService;

    @BeforeEach
    void setUp() {
        matchService = new MatchService(matchRepository);
    }

    @Test
    void creaUnPartidoConDosEquiposDistintos() {
        CreateMatchRequestDTO request = new CreateMatchRequestDTO("team-river", "team-racing", null);

        MatchDTO result = matchService.createMatch(request);

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        Match saved = captor.getValue();
        assertThat(saved.getId()).isNull(); // Mongo le asigna un ObjectId al guardar.
        assertThat(saved.getHomeTeamId()).isEqualTo("team-river");
        assertThat(saved.getAwayTeamId()).isEqualTo("team-racing");
        assertThat(saved.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(saved.getHomeScore()).isNull();
        assertThat(result.homeTeamId()).isEqualTo("team-river");
        assertThat(result.status()).isEqualTo("SCHEDULED");
    }

    @Test
    void usaLaFechaActualSiNoSeEnviaScheduledAt() {
        Instant before = Instant.now();
        matchService.createMatch(new CreateMatchRequestDTO("team-river", "team-racing", null));
        Instant after = Instant.now();

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        assertThat(captor.getValue().getScheduledAt()).isBetween(before, after);
    }

    @Test
    void rechazaElMismoEquipoComoLocalYVisitante() {
        CreateMatchRequestDTO request = new CreateMatchRequestDTO("team-river", "team-river", null);

        assertThatThrownBy(() -> matchService.createMatch(request))
                .isInstanceOf(MatchService.InvalidMatchException.class);
        verify(matchRepository, never()).save(any());
    }

    @Test
    void rechazaUnPartidoSinHomeTeamId() {
        CreateMatchRequestDTO request = new CreateMatchRequestDTO(null, "team-river", null);

        assertThatThrownBy(() -> matchService.createMatch(request))
                .isInstanceOf(MatchService.InvalidMatchException.class);
        verify(matchRepository, never()).save(any());
    }
}
