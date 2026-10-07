package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.domain.Fan;
import com.tpnube.fan_identity_service.domain.League;
import com.tpnube.fan_identity_service.domain.Subscription;
import com.tpnube.fan_identity_service.domain.Team;
import com.tpnube.fan_identity_service.repository.FanRepository;
import com.tpnube.fan_identity_service.repository.SubscriptionRepository;
import com.tpnube.fan_identity_service.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private FanRepository fanRepository;

    private SubscriptionService subscriptionService;

    private final League league = new League("liga-profesional", "Liga Profesional de Fútbol");
    private final Team team = new Team(1L, "Colón", league);

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(subscriptionRepository, teamRepository, fanRepository);
    }

    @Test
    void seguirElMismoEquipoDosVecesNoDuplicaLaSuscripcion() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        Fan fan = new Fan(1L, "fan@quienjuega.com", "hash", null);
        when(subscriptionRepository.findByFanIdAndTeamId(1L, 1L))
                .thenReturn(Optional.of(new Subscription(10L, fan, team)));

        SubscriptionService.SubscriptionResult result = subscriptionService.subscribe(1L, "1");

        assertThat(result.isNew()).isFalse();
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void seguirUnEquipoNuevoCreaLaSuscripcion() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(subscriptionRepository.findByFanIdAndTeamId(1L, 1L)).thenReturn(Optional.empty());
        when(fanRepository.getReferenceById(1L)).thenReturn(new Fan(1L, "fan@quienjuega.com", "hash", null));

        SubscriptionService.SubscriptionResult result = subscriptionService.subscribe(1L, "1");

        assertThat(result.isNew()).isTrue();
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void seguirUnEquipoInexistenteRechaza() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.subscribe(1L, "99"))
                .isInstanceOf(SubscriptionService.TeamNotFoundException.class);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void seguirUnTeamIdNoNumericoRechazaSinConsultarElRepositorio() {
        assertThatThrownBy(() -> subscriptionService.subscribe(1L, "team-fantasma"))
                .isInstanceOf(SubscriptionService.TeamNotFoundException.class);
        verify(teamRepository, never()).findById(any());
        verify(subscriptionRepository, never()).save(any());
    }
}
