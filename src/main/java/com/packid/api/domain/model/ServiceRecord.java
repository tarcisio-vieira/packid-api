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
@Table(name = "service_record")
public class ServiceRecord extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "service_provider_registry_entry_id", nullable = false, columnDefinition = "uuid")
    private UUID serviceProviderRegistryEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_provider_registry_entry_id", insertable = false, updatable = false)
    private RegistryEntry serviceProvider;

    @Column(name = "service_company_id", columnDefinition = "uuid")
    private UUID serviceCompanyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_company_id", insertable = false, updatable = false)
    private ServiceCompany serviceCompany;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_scope", nullable = false, length = 20)
    private ServiceScope serviceScope;

    @Column(name = "block", length = 30)
    private String block;

    @Column(name = "apartment", length = 30)
    private String apartment;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(name = "service_description", nullable = false, length = 500)
    private String serviceDescription;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public enum ServiceScope { UNIT, CONDOMINIUM }
}
