package com.packid.api.domain.repository;

import com.packid.api.domain.model.AmenitySpace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AmenitySpaceRepository extends JpaRepository<AmenitySpace, UUID> {
    List<AmenitySpace> findAllByTenantIdAndDeletedFalseOrderByNameAsc(UUID tenantId);
    List<AmenitySpace> findAllByTenantIdAndActiveTrueAndDeletedFalseOrderByNameAsc(UUID tenantId);
    Optional<AmenitySpace> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
}
