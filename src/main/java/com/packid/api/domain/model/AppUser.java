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
@Table(
        name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_app_user_tenant_email", columnNames = {"tenant_id", "email"}),
                @UniqueConstraint(name = "uq_app_user_tenant_provider_subject", columnNames = {"tenant_id", "provider", "provider_subject"}),
                // necessário se pack_id usar FK composta (tenant_id, registered_by_user_id / handed_over_by_user_id)
                @UniqueConstraint(name = "uq_app_user_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
public class AppUser extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "person_id", columnDefinition = "uuid")
    private UUID personId;

    /**
     * FK composta (tenant_id, person_id) -> person(tenant_id, id)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "person_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Person person;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private AuthProvider provider = AuthProvider.GOOGLE;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @PrePersist
    void syncTenant() {
        if (tenantId == null && tenant != null) {
            tenantId = tenant.getId();
        }
        if (tenantId == null && person != null) {
            tenantId = person.getTenantId();
        }
    }

    public enum AuthProvider {
        GOOGLE
    }
}
