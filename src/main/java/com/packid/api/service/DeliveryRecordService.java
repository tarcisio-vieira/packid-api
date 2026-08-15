package com.packid.api.service;

import com.packid.api.controller.registry.dto.DeliveryRecordRequest;
import com.packid.api.controller.registry.dto.DeliveryRecordResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.DeliveryRecord;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.DeliveryRecordRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryRecordService {

    private final DeliveryRecordRepository repository;
    private final RegistryEntryRepository registryRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public DeliveryRecordService(
            DeliveryRecordRepository repository,
            RegistryEntryRepository registryRepository,
            AuthenticatedUserService authenticatedUserService
    ) {
        this.repository = repository;
        this.registryRepository = registryRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public DeliveryRecordResponse create(OidcUser oidcUser, DeliveryRecordRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry deliveryPerson = requireDeliveryPerson(appUser.getTenantId(), request.deliveryPersonRegistryEntryId());

        DeliveryRecord record = new DeliveryRecord();
        record.setTenantId(appUser.getTenantId());
        record.setDeliveryPersonRegistryEntryId(deliveryPerson.getId());
        record.setBlock(required(request.block(), "Bloco é obrigatório."));
        record.setApartment(required(request.apartment(), "Apartamento é obrigatório."));
        record.setDeliveredAt(request.deliveredAt() == null ? LocalDateTime.now() : request.deliveredAt());
        record.setAuthorizedToEnter(Boolean.TRUE.equals(request.authorizedToEnter()));
        record.setNotes(clean(request.notes()));
        record.setCreatedBy(actor(appUser));

        return toResponse(repository.save(record), deliveryPerson);
    }

    public List<DeliveryRecordResponse> getByDeliveryPerson(OidcUser oidcUser, UUID deliveryPersonId) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry deliveryPerson = requireDeliveryPerson(appUser.getTenantId(), deliveryPersonId);
        return repository
                .findAllByTenantIdAndDeliveryPersonRegistryEntryIdAndDeletedFalseOrderByDeliveredAtDesc(
                        appUser.getTenantId(), deliveryPersonId)
                .stream()
                .map(item -> toResponse(item, deliveryPerson))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryRecordResponse> getByUnit(AppUser appUser, String block, String apartment) {
        return getByUnit(appUser, block, apartment, null, null);
    }

    @Transactional(readOnly = true)
    public List<DeliveryRecordResponse> getByUnit(
            AppUser appUser, String block, String apartment, LocalDateTime from, LocalDateTime to
    ) {
        String cleanedBlock = required(block, "Bloco é obrigatório.");
        String cleanedApartment = required(apartment, "Apartamento é obrigatório.");

        List<DeliveryRecord> records;
        if (from != null && to != null) {
            records = repository.findByUnitBetween(appUser.getTenantId(), cleanedBlock, cleanedApartment, from, to);
        } else if (from != null) {
            records = repository.findByUnitFrom(appUser.getTenantId(), cleanedBlock, cleanedApartment, from);
        } else if (to != null) {
            records = repository.findByUnitUntil(appUser.getTenantId(), cleanedBlock, cleanedApartment, to);
        } else {
            records = repository.findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByDeliveredAtDesc(
                    appUser.getTenantId(), cleanedBlock, cleanedApartment);
        }

        return records.stream()
                .map(item -> toResponse(item, item.getDeliveryPerson()))
                .toList();
    }

    private RegistryEntry requireDeliveryPerson(UUID tenantId, UUID id) {
        RegistryEntry deliveryPerson = registryRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entregador não encontrado."));
        if (deliveryPerson.getEntryType() != RegistryEntry.EntryType.DELIVERY_PERSON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O cadastro informado não é de entregador.");
        }
        return deliveryPerson;
    }

    private DeliveryRecordResponse toResponse(DeliveryRecord record, RegistryEntry deliveryPerson) {
        return new DeliveryRecordResponse(
                record.getId(),
                record.getDeliveryPersonRegistryEntryId(),
                deliveryPerson == null ? null : deliveryPerson.getName(),
                deliveryPerson == null ? null : deliveryPerson.getCompany(),
                deliveryPerson == null ? null : deliveryPerson.getDocument(),
                deliveryPerson == null ? null : deliveryPerson.getPhone(),
                record.getBlock(),
                record.getApartment(),
                record.getDeliveredAt(),
                record.getAuthorizedToEnter(),
                record.getNotes()
        );
    }

    private String actor(AppUser appUser) {
        return appUser.getEmail() == null || appUser.getEmail().isBlank() ? "system" : appUser.getEmail().trim();
    }

    private String required(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return cleaned;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
