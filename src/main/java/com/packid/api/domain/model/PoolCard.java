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
@Table(name = "pool_card")
public class PoolCard extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "resident_registry_entry_id", nullable = false, columnDefinition = "uuid")
    private UUID residentRegistryEntryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_registry_entry_id", insertable = false, updatable = false)
    private RegistryEntry resident;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "validity_months", nullable = false)
    private Integer validityMonths;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "under_ten", nullable = false)
    private Boolean underTen = false;

    @Column(name = "medical_report_drive_file_id", length = 255)
    private String medicalReportDriveFileId;

    @Column(name = "medical_report_mime_type", length = 100)
    private String medicalReportMimeType;

    @Column(name = "medical_report_file_name", length = 255)
    private String medicalReportFileName;

    @Column(name = "medical_report_owner_email", length = 255)
    private String medicalReportOwnerEmail;
}
