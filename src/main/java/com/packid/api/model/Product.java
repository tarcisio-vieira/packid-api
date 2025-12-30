package com.packid.api.model;

import com.packid.api.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "product",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_tenant_code", columnNames = {"tenant_id", "code"}))
public class Product extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    /**
     * FK composta no banco: (tenant_id, unit_of_measure_id) -> unit_of_measure(tenant_id, id)
     * Tenant_id vem do Product. O join usa tenant_id (somente leitura) + unit_of_measure_id.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "unit_of_measure_id", referencedColumnName = "id", nullable = false)
    })
    private UnitOfMeasure unitOfMeasure;
}
