package com.packid.api.model;

import com.packid.api.domain.PackageType;
import com.packid.api.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    // Confirmação do morador (clique/aceite)
    @Column(name = "resident_acknowledged_at")
    private LocalDateTime residentAcknowledgedAt;

    // Entrega em mãos (opcional separar do clique)
    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;

    @Column(name = "handed_over_by_user_id", columnDefinition = "uuid")
    private UUID handedOverByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handed_over_by_user_id", insertable = false, updatable = false)
    private AppUser handedOverBy;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /** Conveniência: bloco e apto vem de apartment->block */
    @Transient
    public String getApartmentCode() {
        if (apartment == null) return null;
        return apartment.getApartmentCode(); // ex.: 2608 / 11102 / 41201
    }
}
