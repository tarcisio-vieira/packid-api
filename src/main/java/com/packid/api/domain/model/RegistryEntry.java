package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "registry_entry")
public class RegistryEntry extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "person_id", columnDefinition = "uuid")
    private UUID personId;

    @Column(name = "occupancy_id", columnDefinition = "uuid")
    private UUID occupancyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupancy_id", referencedColumnName = "id", insertable = false, updatable = false)
    private ApartmentOccupancy occupancy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
            @JoinColumn(name = "person_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private EntryType entryType;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "document", length = 40)
    private String document;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "unit_owner", nullable = false)
    private Boolean unitOwner = false;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "profession", length = 120)
    private String profession;

    @Column(name = "pne", nullable = false)
    private Boolean pne = false;

    @Column(name = "block", length = 30)
    private String block;

    @Column(name = "apartment", length = 30)
    private String apartment;

    @Column(name = "company", length = 150)
    private String company;

    @Column(name = "service_company_id", columnDefinition = "uuid")
    private UUID serviceCompanyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_company_id", insertable = false, updatable = false)
    private ServiceCompany serviceCompany;

    @Column(name = "owner_name", length = 200)
    private String ownerName;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "color", length = 60)
    private String color;

    @Column(name = "identifier", length = 80)
    private String identifier;

    @Column(name = "species", length = 80)
    private String species;

    @Column(name = "breed", length = 100)
    private String breed;

    @Column(name = "pet_size", length = 40)
    private String petSize;

    @Column(name = "parking_space", length = 40)
    private String parkingSpace;

    @Column(name = "parking_space_rented", nullable = false)
    private Boolean parkingSpaceRented = false;

    @Column(name = "parking_space_rental_notes", length = 250)
    private String parkingSpaceRentalNotes;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    // A imagem não é armazenada no banco. Estes campos guardam apenas a referência
    // do arquivo privado criado no Google Drive da conta que fez o upload.
    @Column(name = "photo_drive_file_id", length = 255)
    private String photoDriveFileId;

    @Column(name = "photo_mime_type", length = 100)
    private String photoMimeType;

    @Column(name = "photo_file_name", length = 255)
    private String photoFileName;

    @Column(name = "photo_owner_email", length = 255)
    private String photoOwnerEmail;

    @Column(name = "document_photo_drive_file_id", length = 255)
    private String documentPhotoDriveFileId;

    @Column(name = "document_photo_mime_type", length = 100)
    private String documentPhotoMimeType;

    @Column(name = "document_photo_file_name", length = 255)
    private String documentPhotoFileName;

    @Column(name = "document_photo_owner_email", length = 255)
    private String documentPhotoOwnerEmail;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public enum EntryType {
        RESIDENT,
        DELIVERY_PERSON,
        VISITOR,
        BICYCLE,
        PET,
        VEHICLE,
        SERVICE_PROVIDER
    }
}
