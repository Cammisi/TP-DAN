package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueRepository extends JpaRepository<League, String> {

    List<League> findAllByOrderByName();
}
