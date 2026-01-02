package com.packid.api.service;

import com.packid.api.controller.person.dto.PersonCreateRequest;
import com.packid.api.controller.person.dto.PersonResponse;
import com.packid.api.controller.person.dto.PersonUpdateRequest;
import com.packid.api.domain.model.Person;
import com.packid.api.domain.repository.PersonRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PersonService {

    private final PersonRepository repository;

    public PersonService(PersonRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PersonResponse create(PersonCreateRequest req, String actor) {
        // Se você quiser tratar como "único por tenant" (document/email), aqui valida conflito.
        // OBS: no seu banco ainda não tem UniqueConstraint na tabela person, isso é validação "na aplicação".

        if (req.document() != null && !req.document().isBlank()) {
            repository.findByTenantIdAndDocumentAndDeletedFalse(req.tenantId(), req.document())
                    .ifPresent(p -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe pessoa com este documento neste tenant");
                    });
        }

        if (req.email() != null && !req.email().isBlank()) {
            repository.findByTenantIdAndEmailAndDeletedFalse(req.tenantId(), req.email())
                    .ifPresent(p -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe pessoa com este email neste tenant");
                    });
        }

        Person p = new Person();
        p.setTenantId(req.tenantId());
        p.setFullName(req.fullName());
        p.setDocument(req.document());
        p.setEmail(req.email());
        p.setPhone(req.phone());
        p.setPersonType(req.personType());

        p.setCreatedBy(normalizeActor(actor));

        Person saved = repository.save(p);
        return toResponse(saved);
    }

    public PersonResponse getById(UUID tenantId, UUID id) {
        Person p = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pessoa não encontrada"));
        return toResponse(p);
    }

    public List<PersonResponse> getAll(UUID tenantId) {
        return repository.findAllByTenantIdAndDeletedFalse(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PersonResponse update(UUID tenantId, UUID id, PersonUpdateRequest req, String actor) {
        Person p = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pessoa não encontrada"));

        if (req.fullName() != null) p.setFullName(req.fullName());
        if (req.phone() != null) p.setPhone(req.phone());
        if (req.personType() != null) p.setPersonType(req.personType());

        // document (se mudar, valida conflito)
        if (req.document() != null && !req.document().equalsIgnoreCase(p.getDocument())) {
            if (!req.document().isBlank()) {
                repository.findByTenantIdAndDocumentAndDeletedFalse(tenantId, req.document())
                        .ifPresent(other -> {
                            if (!other.getId().equals(id)) {
                                throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe pessoa com este documento neste tenant");
                            }
                        });
            }
            p.setDocument(req.document());
        }

        // email (se mudar, valida conflito)
        if (req.email() != null && (p.getEmail() == null || !req.email().equalsIgnoreCase(p.getEmail()))) {
            if (!req.email().isBlank()) {
                repository.findByTenantIdAndEmailAndDeletedFalse(tenantId, req.email())
                        .ifPresent(other -> {
                            if (!other.getId().equals(id)) {
                                throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe pessoa com este email neste tenant");
                            }
                        });
            }
            p.setEmail(req.email());
        }

        p.setUpdatedBy(normalizeActor(actor));

        Person saved = repository.save(p);
        return toResponse(saved);
    }

    @Transactional
    public void logicalDelete(UUID tenantId, UUID id, String actor) {
        Person p = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pessoa não encontrada"));

        p.setDeleted(true);
        p.setDeletedAt(LocalDateTime.now());
        p.setDeletedBy(normalizeActor(actor));

        repository.save(p);
    }

    private PersonResponse toResponse(Person p) {
        return new PersonResponse(
                p.getId(),
                p.getTenantId(),
                p.getFullName(),
                p.getDocument(),
                p.getEmail(),
                p.getPhone(),
                p.getPersonType(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor.trim();
    }
}
