package com.packid.api.domain.model.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity extends BaseUuidEntity {

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", insertable = false)
    private LocalDateTime deletedAt;

    @Column(name = "created_by", updatable = false, length = 150)
    private String createdBy;

    @Column(name = "updated_by", length = 150, insertable = false)
    private String updatedBy;

    @Column(name = "deleted_by", length = 150, insertable = false)
    private String deletedBy;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted;

    @PrePersist
    protected final void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (deleted == null) deleted = false;
        if (createdBy == null || createdBy.isBlank()) createdBy = "system";

        prePersistHook();
    }

    @PreUpdate
    protected final void onUpdate() {
        updatedAt = LocalDateTime.now();
        preUpdateHook();
    }

    // Subclasses sobrescrevem SEM @PrePersist/@PreUpdate
    protected void prePersistHook() {}
    protected void preUpdateHook() {}
}
