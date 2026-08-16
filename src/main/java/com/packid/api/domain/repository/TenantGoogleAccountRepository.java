package com.packid.api.domain.repository;

import com.packid.api.domain.model.TenantGoogleAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantGoogleAccountRepository extends JpaRepository<TenantGoogleAccount, UUID> {
    Optional<TenantGoogleAccount> findByTenantIdAndDeletedFalse(UUID tenantId);
}
