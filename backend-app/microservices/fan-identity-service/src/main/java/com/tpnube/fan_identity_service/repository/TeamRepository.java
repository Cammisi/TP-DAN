package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, String> {
}
