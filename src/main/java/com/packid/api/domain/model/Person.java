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
        name = "person",
        uniqueConstraints = {
                // necessário se alguém fizer FK composta (tenant_id, person_id)
                @UniqueConstraint(name = "uq_person_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
public class Person extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "document", length = 40)
    private String document;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 30)
    private PersonType personType;

    @PrePersist
    void syncTenant() {
        if (tenantId == null && tenant != null) {
            tenantId = tenant.getId();
        }
    }

    public enum PersonType {
        OWNER, RESIDENT, TENANT, VISITOR
    }
}
