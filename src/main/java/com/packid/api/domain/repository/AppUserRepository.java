package com.packid.api.domain.repository;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.AppUser.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<AppUser> findByTenantIdAndProviderAndProviderSubject(UUID tenantId, AuthProvider provider, String providerSubject);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndProviderAndProviderSubject(UUID tenantId, AuthProvider provider, String providerSubject);

    // >>> para API (soft delete)
    Optional<AppUser> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);

    List<AppUser> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    Optional<AppUser> findByTenantIdAndEmailAndDeletedFalse(UUID tenantId, String email);

    Optional<AppUser> findByTenantIdAndProviderAndProviderSubjectAndDeletedFalse(
            UUID tenantId,
            AuthProvider provider,
            String providerSubject
    );
}
