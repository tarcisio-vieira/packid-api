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
        name = "condominium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_condominium_tenant_name", columnNames = {"tenant_id", "name"}),
                // útil quando você usa FK composta (tenant_id, condominium_id) em outras tabelas
                @UniqueConstraint(name = "uq_condominium_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
public class Condominium extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", referencedColumnName = "id", insertable = false, updatable = false)
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

    @Column(name = "document_number", length = 30)
    private String documentNumber;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "manager_name", length = 160)
    private String managerName;

    @Column(name = "whatsapp", length = 30)
    private String whatsapp;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "email_notifications_enabled", nullable = false)
    private Boolean emailNotificationsEnabled = Boolean.TRUE;

    @Column(name = "packid_print_two_labels", nullable = false)
    private Boolean packIdPrintTwoLabels = Boolean.TRUE;

    @PrePersist
    void syncTenant() {
        if (tenantId == null && tenant != null) {
            tenantId = tenant.getId();
        }
    }
}
