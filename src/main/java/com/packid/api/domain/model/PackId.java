package com.packid.api.domain.model;

import com.packid.api.domain.type.PackageType;
import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "pack_id")
public class PackId extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "residential_unit_id", nullable = false, columnDefinition = "uuid")
    private UUID residentialUnitId;

    // FK composta: (tenant_id, residential_unit_id) -> residential_unit(tenant_id, id)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "residential_unit_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private ResidentialUnit residentialUnit;

    @Column(name = "person_id", nullable = false, columnDefinition = "uuid")
    private UUID personId;

    // FK composta: (tenant_id, person_id) -> person(tenant_id, id)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "person_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Person person;

    @Column(name = "registered_by_user_id", columnDefinition = "uuid")
    private UUID registeredByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "registered_by_user_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private AppUser registeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 20)
    private PackageType packageType;

    @Lob
    @Column(name = "package_code", columnDefinition = "text")
    private String packageCode;

    @Column(name = "package_code_hash", length = 64)
    private String packageCodeHash;

    @Column(name = "label_package_code", columnDefinition = "text")
    private String labelPackageCode;

    @Column(name = "carrier", length = 80)
    private String carrier;

    @Column(name = "tracking_code", length = 80)
    private String trackingCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "arrived_at", nullable = false)
    private LocalDateTime arrivedAt;

    @Column(name = "whatsapp_message_id", length = 120)
    private String whatsappMessageId;

    @Column(name = "whatsapp_sent_at")
    private LocalDateTime whatsappSentAt;

    @Column(name = "whatsapp_delivered_at")
    private LocalDateTime whatsappDeliveredAt;

    @Column(name = "whatsapp_read_at")
    private LocalDateTime whatsappReadAt;

    @Column(name = "resident_acknowledged_at")
    private LocalDateTime residentAcknowledgedAt;

    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;

    @Column(name = "handed_over_by_user_id", columnDefinition = "uuid")
    private UUID handedOverByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "handed_over_by_user_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private AppUser handedOverBy;

    @Column(name = "observations", columnDefinition = "text")
    private String observations;

    @Override
    protected void prePersistHook() {
        computePackageCodeHash();
        if (arrivedAt == null) arrivedAt = LocalDateTime.now();
    }

    @Override
    protected void preUpdateHook() {
        computePackageCodeHash();
    }

    private void computePackageCodeHash() {
        this.packageCodeHash = sha256Hex(packageCode);
    }

    private static String sha256Hex(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar SHA-256 do packageCode", e);
        }
    }

    @Transient
    public String getUnitCode() {
        return residentialUnit != null ? residentialUnit.getCode() : null;
    }
}

