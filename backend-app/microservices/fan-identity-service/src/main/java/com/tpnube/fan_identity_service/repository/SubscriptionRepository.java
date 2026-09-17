package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
}
