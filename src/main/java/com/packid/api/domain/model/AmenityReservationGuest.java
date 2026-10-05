package com.packid.api.domain.model;

import com.packid.api.domain.model.base.BaseUuidEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "amenity_reservation_guest")
public class AmenityReservationGuest extends BaseUuidEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "reservation_id", nullable = false, columnDefinition = "uuid")
    private UUID reservationId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "document", length = 80)
    private String document;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (createdBy == null || createdBy.isBlank()) createdBy = "system";
        if (deleted == null) deleted = false;
    }
}
