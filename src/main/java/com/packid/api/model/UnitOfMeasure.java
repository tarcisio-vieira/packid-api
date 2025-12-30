package com.packid.api.model;

import com.packid.api.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "unit_of_measure",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_uom_tenant_code", columnNames = {"tenant_id", "code"}),
                @UniqueConstraint(name = "uq_uom_tenant_id_id", columnNames = {"tenant_id", "id"})
        })
public class UnitOfMeasure extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "symbol", length = 20)
    private String symbol;
}
