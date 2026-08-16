package com.packid.api.domain.repository;

import com.packid.api.domain.model.ServiceCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceCompanyRepository extends JpaRepository<ServiceCompany, UUID> {
    List<ServiceCompany> findAllByTenantIdAndDeletedFalseOrderByNameAsc(UUID tenantId);
    Optional<ServiceCompany> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
}
