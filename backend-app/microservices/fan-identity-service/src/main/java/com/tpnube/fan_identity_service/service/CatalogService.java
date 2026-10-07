package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.dto.LeagueDTO;
import com.tpnube.fan_identity_service.dto.TeamCatalogDTO;
import com.tpnube.fan_identity_service.repository.LeagueRepository;
import com.tpnube.fan_identity_service.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
