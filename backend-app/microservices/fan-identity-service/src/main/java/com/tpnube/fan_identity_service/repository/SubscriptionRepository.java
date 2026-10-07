package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByFanIdAndTeamId(Long fanId, Long teamId);

    boolean existsByFanIdAndTeamId(Long fanId, Long teamId);

    void deleteByFanIdAndTeamId(Long fanId, Long teamId);

    @Query("SELECT s FROM Subscription s JOIN FETCH s.team t JOIN FETCH t.league l WHERE s.fan.id = :fanId")
    List<Subscription> findAllByFanIdWithTeamAndLeague(@Param("fanId") Long fanId);

    @Query("SELECT s FROM Subscription s JOIN FETCH s.fan WHERE s.team.id = :teamId")
    List<Subscription> findAllByTeamIdWithFan(@Param("teamId") Long teamId);
}
