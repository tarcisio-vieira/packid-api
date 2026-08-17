package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "apartment_occupancy")
public class ApartmentOccupancy extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "block", nullable = false, length = 30)
    private String block;

    @Column(name = "apartment", nullable = false, length = 30)
    private String apartment;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "resident_access_enabled", nullable = false)
    private Boolean residentAccessEnabled = Boolean.FALSE;

    @Column(name = "resident_username", length = 100)
    private String residentUsername;

    @Column(name = "resident_password_hash", length = 255)
    private String residentPasswordHash;

    @Column(name = "resident_must_change_password", nullable = false)
    private Boolean residentMustChangePassword = Boolean.TRUE;

    @Column(name = "credential_email_enabled", nullable = false)
    private Boolean credentialEmailEnabled = Boolean.FALSE;

    public enum Status {
        ACTIVE,
        SCHEDULED,
        ENDED
    }
}
