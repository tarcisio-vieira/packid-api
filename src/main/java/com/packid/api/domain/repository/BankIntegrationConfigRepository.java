package com.packid.api.domain.repository;

import com.packid.api.domain.model.BankIntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BankIntegrationConfigRepository extends JpaRepository<BankIntegrationConfig, UUID> {
    Optional<BankIntegrationConfig> findByTenantIdAndDeletedFalse(UUID tenantId);
}
