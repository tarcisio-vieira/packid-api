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
@Table(name = "bank_integration_config")
public class BankIntegrationConfig extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider = "NONE";

    @Column(name = "environment", nullable = false, length = 20)
    private String environment = "SANDBOX";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.FALSE;

    @Column(name = "client_id", length = 255)
    private String clientId;

    @Column(name = "client_secret_encrypted", columnDefinition = "text")
    private String clientSecretEncrypted;

    @Column(name = "api_base_url", length = 500)
    private String apiBaseUrl;

    @Column(name = "token_url", length = 500)
    private String tokenUrl;

    @Column(name = "workspace_id", length = 150)
    private String workspaceId;

    @Column(name = "covenant_code", length = 100)
    private String covenantCode;

    @Column(name = "beneficiary_code", length = 100)
    private String beneficiaryCode;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
}
