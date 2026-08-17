package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "space_access_request")
public class SpaceAccessRequest extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "resident_registry_entry_id", nullable = false, columnDefinition = "uuid")
    private UUID residentRegistryEntryId;

    @Column(name = "occupancy_id", columnDefinition = "uuid")
    private UUID occupancyId;

    @Column(name = "block", nullable = false, length = 30)
    private String block;

    @Column(name = "apartment", nullable = false, length = 30)
    private String apartment;

    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 40)
    private SpaceType spaceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private Status status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "return_requested_at")
    private LocalDateTime returnRequestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "released_by", length = 255)
    private String releasedBy;

    @Column(name = "completed_by", length = 255)
    private String completedBy;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public enum SpaceType {
        PLAYROOM,
        GAMES_ROOM,
        GYM,
        SAUNA
    }

    public enum Status {
        REQUESTED_PICKUP,
        IN_USE,
        REQUESTED_RETURN,
        COMPLETED,
        CANCELLED
    }
}
