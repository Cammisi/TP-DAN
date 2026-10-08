package com.tpnube.fan_identity_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fan_id", nullable = false)
    private Fan fan;

    @Column(nullable = false)
    private String message;

    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Column(name = "team_id", nullable = false)
    private String teamId;

    // Equipo que hizo el evento: null en inicio/fin de partido y en las alertas anteriores a T-81.
    @Column(name = "event_team_id")
    private String eventTeamId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
