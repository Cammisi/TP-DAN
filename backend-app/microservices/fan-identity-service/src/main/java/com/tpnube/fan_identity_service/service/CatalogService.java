package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.domain.League;
import com.tpnube.fan_identity_service.domain.Team;
import com.tpnube.fan_identity_service.dto.LeagueDTO;
import com.tpnube.fan_identity_service.dto.TeamCatalogDTO;
import com.tpnube.fan_identity_service.repository.LeagueRepository;
import com.tpnube.fan_identity_service.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
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

    public record LeagueResult(LeagueDTO league, boolean isNew) {}

    // Para "escribir y/o elegir" liga en /admin, igual que con equipos: si el nombre ya
    // existe se reutiliza. El id (a diferencia del de Team, T-77) sigue siendo un slug manual,
    // así que acá se lo genera a partir del nombre en vez de pedirlo.
    @Transactional
    public LeagueResult createLeague(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("El name es obligatorio");
        }

        Optional<League> existing = leagueRepository.findByName(name);
        League league = existing.orElseGet(() -> leagueRepository.save(new League(generateLeagueId(name), name)));

        return new LeagueResult(new LeagueDTO(league.getId(), league.getName()), existing.isEmpty());
    }

    private String generateLeagueId(String name) {
        String base = slugify(name);
        String id = base;
        int suffix = 2;
        while (leagueRepository.existsById(id)) {
            id = base + "-" + suffix++;
        }
        return id;
    }

    private String slugify(String name) {
        String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("[\\p{M}]", "");
        String slug = withoutAccents.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "liga" : slug;
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
