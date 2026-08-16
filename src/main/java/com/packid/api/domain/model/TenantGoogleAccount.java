package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "tenant_google_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tenant_google_account_tenant",
                columnNames = "tenant_id"
        )
)
public class TenantGoogleAccount extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "provider_subject", length = 255)
    private String providerSubject;

    @Column(name = "refresh_token_encrypted", columnDefinition = "text")
    private String refreshTokenEncrypted;

    @Column(name = "drive_enabled", nullable = false)
    private Boolean driveEnabled = true;

    @Column(name = "gmail_enabled", nullable = false)
    private Boolean gmailEnabled = true;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "last_refresh_at")
    private LocalDateTime lastRefreshAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
