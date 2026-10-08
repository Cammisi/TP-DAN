package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.domain.Fan;
import com.tpnube.fan_identity_service.domain.Notification;
import com.tpnube.fan_identity_service.dto.NotificationItemDTO;
import com.tpnube.fan_identity_service.repository.FanRepository;
import com.tpnube.fan_identity_service.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final FanRepository fanRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                FanRepository fanRepository) {
        this.notificationRepository = notificationRepository;
        this.fanRepository = fanRepository;
    }

    @Transactional
    public Long createNotification(String fanId, String eventId, String matchId, String teamId,
                                    String eventTeamId, String message, Instant occurredAt) {
        if (fanId == null || fanId.isBlank()) {
            throw new InvalidRequestException("El fanId es obligatorio");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new InvalidRequestException("El eventId es obligatorio");
        }
        if (matchId == null || matchId.isBlank()) {
            throw new InvalidRequestException("El matchId es obligatorio");
        }
        if (teamId == null || teamId.isBlank()) {
            throw new InvalidRequestException("El teamId es obligatorio");
        }
        if (message == null || message.isBlank()) {
            throw new InvalidRequestException("El message es obligatorio");
        }
        if (occurredAt == null) {
            throw new InvalidRequestException("El occurredAt es obligatorio");
        }

        Long parsedFanId;
        try {
            parsedFanId = Long.parseLong(fanId);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("El fanId debe ser numérico");
        }

        Fan fan = fanRepository.findById(parsedFanId)
                .orElseThrow(() -> new FanNotFoundException(parsedFanId));

        // createdAt guarda el occurredAt del evento deportivo que originó la notificación
        // (cuándo pasó, no cuándo se insertó la fila): es la fecha que tiene sentido
        // mostrarle al fan y la que espera el contrato en la respuesta del listado.
        Notification notification = new Notification(null, fan, message, matchId, teamId, eventTeamId, eventId, occurredAt);
        notificationRepository.save(notification);
        log.info("Notificación persistida: id={}, fanId={}, eventId={}, matchId={}",
                notification.getId(), fanId, eventId, matchId);
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
                        n.getEventTeamId(),
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

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
