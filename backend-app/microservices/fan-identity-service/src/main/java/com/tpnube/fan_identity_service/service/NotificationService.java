package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.domain.Fan;
import com.tpnube.fan_identity_service.domain.Notification;
import com.tpnube.fan_identity_service.dto.NotificationItemDTO;
import com.tpnube.fan_identity_service.repository.FanRepository;
import com.tpnube.fan_identity_service.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FanRepository fanRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                FanRepository fanRepository) {
        this.notificationRepository = notificationRepository;
        this.fanRepository = fanRepository;
    }

    @Transactional
    public Long createNotification(Long fanId, String eventId, String matchId, String teamId,
                                    String message, Instant occurredAt) {
        Fan fan = fanRepository.findById(fanId)
                .orElseThrow(() -> new FanNotFoundException(fanId));

        Notification notification = new Notification(null, fan, message, matchId, teamId, eventId, occurredAt);
        notificationRepository.save(notification);
        return notification.getId();
    }

    @Transactional(readOnly = true)
    public List<NotificationItemDTO> getNotifications(Long fanId) {
        return notificationRepository.findAllByFanIdOrderByCreatedAtDesc(fanId)
                .stream()
                .map(n -> new NotificationItemDTO(
                        n.getId().toString(),
                        n.getMessage(),
                        n.getMatchId(),
                        n.getTeamId(),
                        n.getEventId(),
                        n.getCreatedAt()
                ))
                .toList();
    }

    public static class FanNotFoundException extends RuntimeException {
        public FanNotFoundException(Long fanId) {
            super("El fan '" + fanId + "' no existe");
        }
    }
}
