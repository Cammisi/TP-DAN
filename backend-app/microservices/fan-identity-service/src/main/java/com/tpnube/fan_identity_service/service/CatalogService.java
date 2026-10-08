package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.domain.League;
import com.tpnube.fan_identity_service.domain.Team;
import com.tpnube.fan_identity_service.dto.LeagueDTO;
import com.tpnube.fan_identity_service.dto.TeamCatalogDTO;
import com.tpnube.fan_identity_service.repository.LeagueRepository;
import com.tpnube.fan_identity_service.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CatalogService {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;

    public CatalogService(LeagueRepository leagueRepository, TeamRepository teamRepository) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<LeagueDTO> getLeagues() {
        return leagueRepository.findAllByOrderByName()
                .stream()
                .map(league -> new LeagueDTO(league.getId(), league.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamCatalogDTO> searchTeams(String name, String leagueId) {
        return teamRepository.search(name, leagueId)
                .stream()
                .map(team -> new TeamCatalogDTO(
                        String.valueOf(team.getId()),
                        team.getName(),
                        team.getLeague().getId(),
                        team.getLeague().getName()
                ))
                .toList();
    }

    public record TeamResult(TeamCatalogDTO team, boolean isNew) {}

    // Para "escribir y/o elegir" equipo en /admin (T-76): si el nombre ya existe se reutiliza,
    // así no se duplica un equipo por tipearlo de nuevo en otro partido.
    @Transactional
    public TeamResult createTeam(String name, String leagueId) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("El name es obligatorio");
        }
        if (leagueId == null || leagueId.isBlank()) {
            throw new InvalidRequestException("El leagueId es obligatorio");
        }

        Optional<Team> existing = teamRepository.findByName(name);
        Team team = existing.orElseGet(() -> {
            League league = leagueRepository.findById(leagueId)
                    .orElseThrow(() -> new LeagueNotFoundException(leagueId));
            return teamRepository.save(new Team(null, name, league));
        });

        TeamCatalogDTO dto = new TeamCatalogDTO(
                String.valueOf(team.getId()),
                team.getName(),
                team.getLeague().getId(),
                team.getLeague().getName()
        );
        return new TeamResult(dto, existing.isEmpty());
    }

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    public static class LeagueNotFoundException extends RuntimeException {
        public LeagueNotFoundException(String leagueId) {
            super("La liga '" + leagueId + "' no existe");
        }
    }
}
