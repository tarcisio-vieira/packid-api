package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "managed_document", indexes = {
        @Index(name = "idx_managed_document_tenant_category", columnList = "tenant_id, category, deleted")
})
public class ManagedDocument extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private Category category;

    @Column(name = "display_name", nullable = false, length = 220)
    private String displayName;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Column(name = "mime_type", length = 180)
    private String mimeType;

    @Column(name = "file_extension", length = 24)
    private String fileExtension;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "drive_file_id", nullable = false, length = 255)
    private String driveFileId;

    public enum Category {
        LIBRARY,
        INTERNAL_REGULATION
    }
}
