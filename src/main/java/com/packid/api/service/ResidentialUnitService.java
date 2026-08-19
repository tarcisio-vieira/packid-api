package com.packid.api.service;

import com.packid.api.controller.residentialUnit.dto.ResidentialUnitCreateRequest;
import com.packid.api.controller.residentialUnit.dto.ResidentialUnitResponse;
import com.packid.api.controller.residentialUnit.dto.ResidentialUnitUpdateRequest;
import com.packid.api.domain.model.ResidentialUnit;
import com.packid.api.domain.repository.ResidentialUnitRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ResidentialUnitService {

    private final ResidentialUnitRepository repository;

    public ResidentialUnitService(ResidentialUnitRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ResidentialUnitResponse create(ResidentialUnitCreateRequest req, String actor) {
        String block = required(req.block(), "Informe o bloco.");
        String apartment = required(req.apartment(), "Informe o apartamento.");
        ensureUnitUnique(req.tenantId(), block, apartment, null);

        String code = clean(req.code());
        if (code == null) code = block + apartment;
        repository.findByCondominiumIdAndCodeAndDeletedFalse(req.condominiumId(), code)
                .ifPresent(u -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe unidade com este código neste condomínio."); });

        ResidentialUnit ru = new ResidentialUnit();
        ru.setTenantId(req.tenantId());
        ru.setCondominiumId(req.condominiumId());
        ru.setCode(code);
        ru.setName(clean(req.name()) == null ? "Bloco " + block + " Apto " + apartment : clean(req.name()));
        ru.setBlock(block);
        ru.setApartment(apartment);
        ru.setActive(req.active() != null ? req.active() : Boolean.TRUE);
        ru.setCreatedBy(normalizeActor(actor));
        return toResponse(repository.save(ru));
    }

    public ResidentialUnitResponse getById(UUID tenantId, UUID id) {
        return toResponse(require(tenantId, id));
    }

    public List<ResidentialUnitResponse> getAll(UUID tenantId) {
        return repository.findAllByTenantIdAndDeletedFalse(tenantId).stream()
                .sorted(Comparator.comparing((ResidentialUnit u) -> clean(u.getBlock()), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(u -> clean(u.getApartment()), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ResidentialUnitResponse update(UUID tenantId, UUID id, ResidentialUnitUpdateRequest req, String actor) {
        ResidentialUnit ru = require(tenantId, id);
        UUID condominiumId = req.condominiumId() != null ? req.condominiumId() : ru.getCondominiumId();
        String block = clean(req.block()) != null ? clean(req.block()) : ru.getBlock();
        String apartment = clean(req.apartment()) != null ? clean(req.apartment()) : ru.getApartment();
        block = required(block, "Informe o bloco.");
        apartment = required(apartment, "Informe o apartamento.");
        ensureUnitUnique(tenantId, block, apartment, id);

        String code = clean(req.code());
        if (code == null && (req.block() != null || req.apartment() != null)) code = block + apartment;
        if (code == null) code = ru.getCode();
        final String finalCode = code;
        repository.findByCondominiumIdAndCodeAndDeletedFalse(condominiumId, finalCode)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe unidade com este código neste condomínio."); });

        ru.setCondominiumId(condominiumId);
        ru.setCode(finalCode);
        ru.setBlock(block);
        ru.setApartment(apartment);
        if (req.name() != null) ru.setName(clean(req.name()));
        if (req.active() != null) ru.setActive(req.active());
        ru.setUpdatedBy(normalizeActor(actor));
        return toResponse(repository.save(ru));
    }

    @Transactional
    public void logicalDelete(UUID tenantId, UUID id, String actor) {
        ResidentialUnit ru = require(tenantId, id);
        ru.setDeleted(true);
        ru.setDeletedAt(LocalDateTime.now());
        ru.setDeletedBy(normalizeActor(actor));
        repository.save(ru);
    }

    private ResidentialUnit require(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidade residencial não encontrada."));
    }

    private void ensureUnitUnique(UUID tenantId, String block, String apartment, UUID ignoreId) {
        repository.findByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalse(tenantId, block, apartment)
                .filter(other -> ignoreId == null || !other.getId().equals(ignoreId))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Este bloco/apartamento já está cadastrado."); });
    }

    private ResidentialUnitResponse toResponse(ResidentialUnit ru) {
        return new ResidentialUnitResponse(
                ru.getId(), ru.getTenantId(), ru.getCondominiumId(), ru.getCode(), ru.getName(),
                ru.getBlock(), ru.getApartment(), ru.getActive(), ru.getCreatedAt(), ru.getUpdatedAt());
    }

    private String normalizeActor(String actor) { return clean(actor) == null ? "system" : actor.trim(); }
    private String required(String value, String message) { String v = clean(value); if (v == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); return v; }
    private String clean(String value) { if (value == null) return null; String v = value.trim(); return v.isBlank() ? null : v; }
}
