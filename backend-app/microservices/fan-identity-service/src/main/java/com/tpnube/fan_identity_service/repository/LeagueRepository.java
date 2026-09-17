package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.League;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueRepository extends JpaRepository<League, String> {
}
