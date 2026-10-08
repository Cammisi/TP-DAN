package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, String> {

    List<League> findAllByOrderByName();

    // Para crear ligas "al vuelo" desde /admin (T-76): si el nombre ya existe, se reutiliza.
    Optional<League> findByName(String name);
}
