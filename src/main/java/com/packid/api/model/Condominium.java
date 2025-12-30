package com.packid.api.model;

import com.packid.api.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "condominium",
        uniqueConstraints = @UniqueConstraint(name = "uq_condominium_tenant_name", columnNames = {"tenant_id", "name"}))
public class Condominium extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 80)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;
}
