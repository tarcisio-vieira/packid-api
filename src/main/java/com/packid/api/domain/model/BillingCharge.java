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
@Table(name = "billing_charge")
public class BillingCharge extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "occupancy_id", nullable = false, columnDefinition = "uuid")
    private UUID occupancyId;

    @Column(name = "bank_provider", length = 40)
    private String bankProvider;

    @Column(name = "external_id", length = 180)
    private String externalId;

    @Column(name = "reference_number", length = 120)
    private String referenceNumber;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "nominal_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal nominalValue;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.PENDING;

    @Column(name = "digitable_line", length = 160)
    private String digitableLine;

    @Column(name = "barcode", length = 160)
    private String barcode;

    @Column(name = "document_url", length = 1000)
    private String documentUrl;

    @Column(name = "bank_payload_reference", length = 255)
    private String bankPayloadReference;

    @Column(name = "last_bank_update_at")
    private LocalDateTime lastBankUpdateAt;

    public enum Status { PENDING, PAID, OVERDUE, CANCELLED }
}
