package com.packid.api.service;

import com.packid.api.controller.servicecompany.dto.ServiceCompanyRequest;
import com.packid.api.controller.servicecompany.dto.ServiceCompanyResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.ServiceCompany;
import com.packid.api.domain.repository.ServiceCompanyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceCompanyService {
    private final ServiceCompanyRepository repository;
    private final AuthenticatedUserService authenticatedUserService;

    public ServiceCompanyService(ServiceCompanyRepository repository, AuthenticatedUserService authenticatedUserService) {
        this.repository = repository;
        this.authenticatedUserService = authenticatedUserService;
    }

    public List<ServiceCompanyResponse> getAll(OidcUser user) {
        AppUser appUser = authenticatedUserService.requireAppUser(user);
        return repository.findAllByTenantIdAndDeletedFalseOrderByNameAsc(appUser.getTenantId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ServiceCompanyResponse create(OidcUser user, ServiceCompanyRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(user);
        ServiceCompany entity = new ServiceCompany();
        entity.setTenantId(appUser.getTenantId());
        entity.setCreatedBy(actor(appUser));
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ServiceCompanyResponse update(OidcUser user, UUID id, ServiceCompanyRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(user);
        ServiceCompany entity = require(appUser, id);
        apply(entity, request);
        entity.setUpdatedBy(actor(appUser));
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(OidcUser user, UUID id) {
        AppUser appUser = authenticatedUserService.requireAppUser(user);
        ServiceCompany entity = require(appUser, id);
        entity.setDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(actor(appUser));
        repository.save(entity);
    }

    public ServiceCompany require(AppUser appUser, UUID id) {
        return repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa prestadora não encontrada."));
    }

    private void apply(ServiceCompany entity, ServiceCompanyRequest r) {
        entity.setName(required(r.name(), "Nome da empresa é obrigatório."));
        entity.setTradeName(clean(r.tradeName()));
        entity.setDocumentNumber(clean(r.documentNumber()));
        entity.setPhone(clean(r.phone()));
        entity.setEmail(clean(r.email()));
        entity.setContactName(clean(r.contactName()));
        entity.setAddressLine(clean(r.addressLine()));
        entity.setCity(clean(r.city()));
        entity.setState(clean(r.state()));
        entity.setZipCode(clean(r.zipCode()));
        entity.setNotes(clean(r.notes()));
        entity.setActive(r.active() == null ? Boolean.TRUE : r.active());
    }

    public ServiceCompanyResponse toResponse(ServiceCompany e) {
        return new ServiceCompanyResponse(e.getId(), e.getName(), e.getTradeName(), e.getDocumentNumber(), e.getPhone(),
                e.getEmail(), e.getContactName(), e.getAddressLine(), e.getCity(), e.getState(), e.getZipCode(), e.getNotes(),
                e.getActive(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String actor(AppUser user) { return clean(user.getEmail()) == null ? "system" : user.getEmail().trim(); }
    private String required(String v, String msg) { String c = clean(v); if (c == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); return c; }
    private String clean(String v) { if (v == null) return null; String c = v.trim(); return c.isBlank() ? null : c; }
}
