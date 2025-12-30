package com.packid.api.model;

import com.packid.api.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "block",
        uniqueConstraints = @UniqueConstraint(name = "uq_block_condominium_code", columnNames = {"condominium_id", "code"}))
public class Block extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "condominium_id", nullable = false, columnDefinition = "uuid")
    private UUID condominiumId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condominium_id", insertable = false, updatable = false)
    private Condominium condominium;

    @Column(name = "code", nullable = false, length = 10)
    private String code; // "1","2","3","4" (ou A/B/C/D se você preferir)

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "floors_count", nullable = false)
    private Short floorsCount = 12;

    @Column(name = "units_per_floor", nullable = false)
    private Short unitsPerFloor = 8;

    @PrePersist
    void syncTenant() {
        if (tenantId == null && condominium != null) {
            tenantId = condominium.getTenantId();
        }
    }
}
