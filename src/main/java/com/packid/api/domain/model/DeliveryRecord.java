package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "delivery_record")
public class DeliveryRecord extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "delivery_person_registry_entry_id", nullable = false, columnDefinition = "uuid")
    private UUID deliveryPersonRegistryEntryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "delivery_person_registry_entry_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private RegistryEntry deliveryPerson;

    @Column(name = "block", nullable = false, length = 30)
    private String block;

    @Column(name = "apartment", nullable = false, length = 30)
    private String apartment;

    @Column(name = "delivered_at", nullable = false)
    private LocalDateTime deliveredAt;

    @Column(name = "authorized_to_enter", nullable = false)
    private Boolean authorizedToEnter = false;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
