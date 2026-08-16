package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "service_company")
public class ServiceCompany extends AuditableEntity {
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "document_number", length = 40)
    private String documentNumber;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 60)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
