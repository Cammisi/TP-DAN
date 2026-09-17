package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.Fan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FanRepository extends JpaRepository<Fan, Long> {

    Optional<Fan> findByEmail(String email);
}
