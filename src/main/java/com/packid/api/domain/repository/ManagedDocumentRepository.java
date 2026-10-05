package com.packid.api.domain.repository;

import com.packid.api.domain.model.ManagedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagedDocumentRepository extends JpaRepository<ManagedDocument, UUID> {
    List<ManagedDocument> findAllByTenantIdAndCategoryAndDeletedFalseOrderByDisplayNameAsc(UUID tenantId, ManagedDocument.Category category);
    Optional<ManagedDocument> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
}
