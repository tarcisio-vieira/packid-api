package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "amenity_space")
public class AmenitySpace extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "usage_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal usageFee = BigDecimal.ZERO;

    @Column(name = "min_advance_days", nullable = false)
    private Integer minAdvanceDays = 7;

    @Column(name = "max_advance_days", nullable = false)
    private Integer maxAdvanceDays = 365;

    @Column(name = "cancellation_days", nullable = false)
    private Integer cancellationDays = 7;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "photo_data", columnDefinition = "bytea")
    private byte[] photoData;

    @Column(name = "photo_mime_type", length = 120)
    private String photoMimeType;

    @Column(name = "photo_file_name", length = 255)
    private String photoFileName;
}
