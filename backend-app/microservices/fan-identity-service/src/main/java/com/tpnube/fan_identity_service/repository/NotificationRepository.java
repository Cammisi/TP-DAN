package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByFanIdOrderByCreatedAtDesc(Long fanId);
}
