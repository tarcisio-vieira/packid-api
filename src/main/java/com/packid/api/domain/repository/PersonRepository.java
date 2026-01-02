package com.packid.api.domain.repository;

import com.packid.api.domain.model.Person;
import com.packid.api.domain.model.Person.PersonType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonRepository extends JpaRepository<Person, UUID> {

    List<Person> findAllByTenantId(UUID tenantId);
    List<Person> findAllByTenantIdAndPersonType(UUID tenantId, PersonType personType);

    Optional<Person> findByTenantIdAndDocument(UUID tenantId, String document);
    boolean existsByTenantIdAndDocument(UUID tenantId, String document);

    Optional<Person> findByTenantIdAndEmail(UUID tenantId, String email);
    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    // >>> soft delete
    Optional<Person> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    List<Person> findAllByTenantIdAndDeletedFalse(UUID tenantId);
    List<Person> findAllByTenantIdAndPersonTypeAndDeletedFalse(UUID tenantId, PersonType personType);

    Optional<Person> findByTenantIdAndDocumentAndDeletedFalse(UUID tenantId, String document);
    Optional<Person> findByTenantIdAndEmailAndDeletedFalse(UUID tenantId, String email);
}
