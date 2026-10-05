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
@Table(name = "announcement")
public class Announcement extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "body_text", nullable = false, columnDefinition = "text")
    private String bodyText;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "recipient_count", nullable = false)
    private Integer recipientCount = 0;

    @Column(name = "delivered_count", nullable = false)
    private Integer deliveredCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    public enum DeliveryStatus { PENDING, SENT, PARTIAL, FAILED }
}
