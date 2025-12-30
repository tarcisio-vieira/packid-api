package com.packid.api.model;

import com.packid.api.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_app_user_tenant_email", columnNames = {"tenant_id", "email"}),
                @UniqueConstraint(name = "uq_app_user_tenant_provider_subject", columnNames = {"tenant_id", "provider", "provider_subject"})
        })
public class AppUser extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "person_id", columnDefinition = "uuid")
    private UUID personId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", insertable = false, updatable = false)
    private Person person;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private AuthProvider provider = AuthProvider.GOOGLE;

    @Column(name = "provider_subject", length = 255)
    private String providerSubject; // "sub" do Google

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public enum AuthProvider {
        GOOGLE
    }
}
