package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "person")
public class Person extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "document", length = 40)
    private String document;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 30)
    private PersonType personType;

    public enum PersonType {
        OWNER, RESIDENT, TENANT, VISITOR
    }
}
