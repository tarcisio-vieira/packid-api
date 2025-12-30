package com.packid.api.model;

import com.packid.api.domain.PackageType;
import com.packid.api.model.base.AuditableEntity;
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

    @Column(name = "apartment_id", nullable = false, columnDefinition = "uuid")
    private UUID apartmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apartment_id", insertable = false, updatable = false)
    private Apartment apartment;

    @Column(name = "person_id", nullable = false, columnDefinition = "uuid")
    private UUID personId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", insertable = false, updatable = false)
    private Person person;

    @Column(name = "registered_by_user_id", columnDefinition = "uuid")
    private UUID registeredByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_user_id", insertable = false, updatable = false)
    private AppUser registeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 20)
    private PackageType packageType;

    // QRCode/código de barras (pode ser grande)
    @Lob
    @Column(name = "package_code", columnDefinition = "text")
    private String packageCode;

    // SHA-256 hex (64 chars)
    @Column(name = "package_code_hash", length = 64)
    private String packageCodeHash;

    @Column(name = "carrier", length = 80)
    private String carrier;

    @Column(name = "tracking_code", length = 80)
    private String trackingCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "arrived_at", nullable = false)
    private LocalDateTime arrivedAt;

    // WhatsApp
    @Column(name = "whatsapp_message_id", length = 120)
    private String whatsappMessageId;

    @Column(name = "whatsapp_sent_at")
    private LocalDateTime whatsappSentAt;

    @Column(name = "whatsapp_delivered_at")
    private LocalDateTime whatsappDeliveredAt;

    @Column(name = "whatsapp_read_at")
    private LocalDateTime whatsappReadAt;

    // Clique/confirmação do morador
    @Column(name = "resident_acknowledged_at")
    private LocalDateTime residentAcknowledgedAt;

    // Entrega em mãos
    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;

    @Column(name = "handed_over_by_user_id", columnDefinition = "uuid")
    private UUID handedOverByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handed_over_by_user_id", insertable = false, updatable = false)
    private AppUser handedOverBy;

    // Observações
    @Column(name = "observations", columnDefinition = "text")
    private String observations;

    @PrePersist
    @PreUpdate
    void computePackageCodeHash() {
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
    public String getApartmentCode() {
        return apartment != null ? apartment.getApartmentCode() : null;
    }
}
