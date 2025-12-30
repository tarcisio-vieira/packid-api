package com.packid.api.model;

import com.packid.api.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "apartment",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_apartment_block_floor_number",
                columnNames = {"block_id", "floor", "apartment_number"}))
public class Apartment extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "block_id", nullable = false, columnDefinition = "uuid")
    private UUID blockId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "block_id", insertable = false, updatable = false)
    private Block block;

    @Column(name = "floor", nullable = false)
    private Short floor; // 1..12

    @Column(name = "apartment_number", nullable = false)
    private Short apartmentNumber; // 1..8

    /** Ex.: 2608, 11102, 41201 */
    @Transient
    public String getApartmentCode() {
        String blockCode = (block != null ? block.getCode() : null);
        if (blockCode == null) blockCode = "?";
        return blockCode + floor + String.format("%02d", apartmentNumber);
    }

    @PrePersist
    void syncTenant() {
        if (tenantId == null && block != null) {
            tenantId = block.getTenantId();
        }
    }
}
