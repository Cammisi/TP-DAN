package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.domain.Fan;
import com.tpnube.fan_identity_service.domain.Subscription;
import com.tpnube.fan_identity_service.domain.Team;
import com.tpnube.fan_identity_service.dto.SubscriberDTO;
import com.tpnube.fan_identity_service.dto.SubscriptionItemDTO;
import com.tpnube.fan_identity_service.dto.SubscriptionResponseDTO;
import com.tpnube.fan_identity_service.repository.FanRepository;
import com.tpnube.fan_identity_service.repository.SubscriptionRepository;
import com.tpnube.fan_identity_service.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final TeamRepository teamRepository;
    private final FanRepository fanRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               TeamRepository teamRepository,
                               FanRepository fanRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.teamRepository = teamRepository;
        this.fanRepository = fanRepository;
    }

    public record SubscriptionResult(SubscriptionResponseDTO response, boolean isNew) {}

    @Transactional
    public SubscriptionResult subscribe(Long fanId, String teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        Optional<Subscription> existing = subscriptionRepository.findByFanIdAndTeamId(fanId, teamId);
        if (existing.isPresent()) {
            SubscriptionResponseDTO dto = new SubscriptionResponseDTO(
                    team.getId(),
                    team.getName(),
                    team.getLeague().getName()
            );
            return new SubscriptionResult(dto, false);
        }

        Fan fan = fanRepository.getReferenceById(fanId);
        Subscription subscription = new Subscription(null, fan, team);
        subscriptionRepository.save(subscription);

        SubscriptionResponseDTO dto = new SubscriptionResponseDTO(
                team.getId(),
                team.getName(),
                team.getLeague().getName()
        );
        return new SubscriptionResult(dto, true);
    }

    @Transactional
    public void unsubscribe(Long fanId, String teamId) {
        subscriptionRepository.deleteByFanIdAndTeamId(fanId, teamId);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionItemDTO> getSubscriptions(Long fanId) {
        return subscriptionRepository.findAllByFanIdWithTeamAndLeague(fanId)
                .stream()
                .map(sub -> new SubscriptionItemDTO(
                        sub.getTeam().getId(),
                        sub.getTeam().getName(),
                        sub.getTeam().getLeague().getId(),
                        sub.getTeam().getLeague().getName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriberDTO> getSubscribers(String teamId) {
        return subscriptionRepository.findAllByTeamIdWithFan(teamId)
                .stream()
                .map(sub -> new SubscriberDTO(
                        sub.getFan().getId().toString(),
                        sub.getFan().getEmail()
                ))
                .toList();
    }

    public static class TeamNotFoundException extends RuntimeException {
        public TeamNotFoundException(String teamId) {
            super("El equipo '" + teamId + "' no existe en el catálogo");
        }
    }
}
