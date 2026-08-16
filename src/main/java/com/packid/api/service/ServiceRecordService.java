package com.packid.api.service;

import com.packid.api.controller.servicerecord.dto.ServiceRecordRequest;
import com.packid.api.controller.servicerecord.dto.ServiceRecordResponse;
import com.packid.api.domain.model.*;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.ServiceCompanyRepository;
import com.packid.api.domain.repository.ServiceRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceRecordService {
    private final ServiceRecordRepository repository;
    private final RegistryEntryRepository registryRepository;
    private final ServiceCompanyRepository companyRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public ServiceRecordService(ServiceRecordRepository repository, RegistryEntryRepository registryRepository,
                                ServiceCompanyRepository companyRepository, AuthenticatedUserService authenticatedUserService) {
        this.repository = repository;
        this.registryRepository = registryRepository;
        this.companyRepository = companyRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public ServiceRecordResponse create(OidcUser user, ServiceRecordRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(user);
        RegistryEntry provider = requireProvider(appUser.getTenantId(), request.serviceProviderRegistryEntryId());
        if (!Boolean.TRUE.equals(provider.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O prestador está inativo. Reative o cadastro antes de registrar um serviço.");
        }

        ServiceRecord record = new ServiceRecord();
        record.setTenantId(appUser.getTenantId());
        record.setServiceProviderRegistryEntryId(provider.getId());
        record.setServiceCompanyId(provider.getServiceCompanyId());
        record.setServiceScope(request.serviceScope());
        if (request.serviceScope() == ServiceRecord.ServiceScope.UNIT) {
            record.setBlock(required(request.block(), "Bloco é obrigatório para serviço em unidade."));
            record.setApartment(required(request.apartment(), "Apartamento é obrigatório para serviço em unidade."));
        } else {
            record.setBlock(null);
            record.setApartment(null);
        }
        record.setPerformedAt(request.performedAt() == null ? LocalDateTime.now() : request.performedAt());
        record.setServiceDescription(required(request.serviceDescription(), "Descrição do serviço é obrigatória."));
        record.setNotes(clean(request.notes()));
        record.setCreatedBy(actor(appUser));
        return toResponse(repository.save(record), provider, resolveCompany(appUser.getTenantId(), provider.getServiceCompanyId()));
    }

    @Transactional(readOnly = true)
    public List<ServiceRecordResponse> get(OidcUser user, UUID providerId, ServiceRecord.ServiceScope scope) {
        AppUser appUser = authenticatedUserService.requireAppUser(user);
        List<ServiceRecord> records;
        if (providerId != null) {
            requireProvider(appUser.getTenantId(), providerId);
            records = repository.findAllByTenantIdAndServiceProviderRegistryEntryIdAndDeletedFalseOrderByPerformedAtDesc(appUser.getTenantId(), providerId);
        } else if (scope != null) {
            records = repository.findAllByTenantIdAndServiceScopeAndDeletedFalseOrderByPerformedAtDesc(appUser.getTenantId(), scope);
        } else {
            records = repository.findAllByTenantIdAndServiceScopeAndDeletedFalseOrderByPerformedAtDesc(appUser.getTenantId(), ServiceRecord.ServiceScope.CONDOMINIUM);
        }
        return records.stream().map(item -> toResponse(item, item.getServiceProvider(), item.getServiceCompany())).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRecordResponse> getByUnit(AppUser appUser, String block, String apartment, LocalDateTime from, LocalDateTime to) {
        List<ServiceRecord> records;
        if (from != null && to != null) records = repository.findByUnitBetween(appUser.getTenantId(), ServiceRecord.ServiceScope.UNIT, block, apartment, from, to);
        else if (from != null) records = repository.findByUnitFrom(appUser.getTenantId(), ServiceRecord.ServiceScope.UNIT, block, apartment, from);
        else if (to != null) records = repository.findByUnitUntil(appUser.getTenantId(), ServiceRecord.ServiceScope.UNIT, block, apartment, to);
        else records = repository.findByUnit(appUser.getTenantId(), ServiceRecord.ServiceScope.UNIT, block, apartment);
        return records.stream().map(item -> toResponse(item, item.getServiceProvider(), item.getServiceCompany())).toList();
    }

    private RegistryEntry requireProvider(UUID tenantId, UUID id) {
        RegistryEntry provider = registryRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador de serviço não encontrado."));
        if (provider.getEntryType() != RegistryEntry.EntryType.SERVICE_PROVIDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O cadastro informado não é de prestador de serviço.");
        }
        return provider;
    }

    private ServiceCompany resolveCompany(UUID tenantId, UUID id) {
        if (id == null) return null;
        return companyRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id).orElse(null);
    }

    private ServiceRecordResponse toResponse(ServiceRecord s, RegistryEntry provider, ServiceCompany company) {
        return new ServiceRecordResponse(s.getId(), s.getServiceProviderRegistryEntryId(), provider == null ? null : provider.getName(),
                s.getServiceCompanyId(), company == null ? null : company.getName(), s.getServiceScope(), s.getBlock(), s.getApartment(),
                s.getPerformedAt(), s.getServiceDescription(), s.getNotes(), s.getCreatedBy());
    }

    private String actor(AppUser user) { return clean(user.getEmail()) == null ? "system" : user.getEmail().trim(); }
    private String required(String v, String msg) { String c = clean(v); if (c == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); return c; }
    private String clean(String v) { if (v == null) return null; String c = v.trim(); return c.isBlank() ? null : c; }
}
