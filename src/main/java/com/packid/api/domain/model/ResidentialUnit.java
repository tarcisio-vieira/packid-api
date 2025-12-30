package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "residential_unit",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_residential_unit_condominium_code", columnNames = {"condominium_id", "code"}),
                // necessário se outras tabelas fizerem FK composta (tenant_id, residential_unit_id)
                @UniqueConstraint(name = "uq_residential_unit_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
public class ResidentialUnit extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "condominium_id", nullable = false, columnDefinition = "uuid")
    private UUID condominiumId;

    /**
     * FK composta (tenant_id, condominium_id) -> condominium(tenant_id, id)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "condominium_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Condominium condominium;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @PrePersist
    void syncTenant() {
        if (tenantId == null && condominium != null) {
            tenantId = condominium.getTenantId();
        }
        if (tenantId == null && tenant != null) {
            tenantId = tenant.getId();
        }
    }
}
