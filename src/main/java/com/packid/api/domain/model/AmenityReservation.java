package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "amenity_reservation")
public class AmenityReservation extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "amenity_space_id", nullable = false, columnDefinition = "uuid")
    private UUID amenitySpaceId;

    @Column(name = "occupancy_id", nullable = false, columnDefinition = "uuid")
    private UUID occupancyId;

    @Column(name = "resident_registry_entry_id", columnDefinition = "uuid")
    private UUID residentRegistryEntryId;

    @Column(name = "block", nullable = false, length = 30)
    private String block;

    @Column(name = "apartment", nullable = false, length = 30)
    private String apartment;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.BOOKED;

    @Column(name = "usage_fee_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal usageFeeSnapshot = BigDecimal.ZERO;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by", length = 150)
    private String cancelledBy;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public enum Status { BOOKED, CANCELLED }
}
