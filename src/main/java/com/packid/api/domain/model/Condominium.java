package com.packid.api.domain.model;

import com.packid.api.domain.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "condominium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_condominium_tenant_name", columnNames = {"tenant_id", "name"}),
                // útil quando você usa FK composta (tenant_id, condominium_id) em outras tabelas
                @UniqueConstraint(name = "uq_condominium_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
public class Condominium extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 80)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "document_number", length = 30)
    private String documentNumber;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "manager_name", length = 160)
    private String managerName;

    @Column(name = "whatsapp", length = 30)
    private String whatsapp;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "email_notifications_enabled", nullable = false)
    private Boolean emailNotificationsEnabled = Boolean.TRUE;

    @Column(name = "resident_credential_emails_enabled", nullable = false)
    private Boolean residentCredentialEmailsEnabled = Boolean.FALSE;

    @Column(name = "packid_print_two_labels", nullable = false)
    private Boolean packIdPrintTwoLabels = Boolean.TRUE;

    @Column(name = "logo_drive_file_id", length = 255)
    private String logoDriveFileId;

    @Column(name = "logo_mime_type", length = 100)
    private String logoMimeType;

    @Column(name = "logo_file_name", length = 255)
    private String logoFileName;

    @Column(name = "logo_owner_email", length = 255)
    private String logoOwnerEmail;

    @Column(name = "pool_card_title", nullable = false, length = 80)
    private String poolCardTitle = "PISCINA";

    @Column(name = "pool_card_subtitle", nullable = false, length = 120)
    private String poolCardSubtitle = "USO DA PISCINA";

    @Column(name = "pool_opening_hours", length = 300)
    private String poolOpeningHours = "Todos os dias das 09h às 17h.";

    @Column(name = "pool_show_opening_hours", nullable = false)
    private Boolean poolShowOpeningHours = Boolean.TRUE;

    @Column(name = "pool_closed_days_message", length = 400)
    private String poolClosedDaysMessage = "Toda segunda-feira fechada para tratamento e manutenção de fundo.";

    @Column(name = "pool_show_closed_days", nullable = false)
    private Boolean poolShowClosedDays = Boolean.TRUE;

    @Column(name = "pool_validity_months", nullable = false)
    private Integer poolValidityMonths = 6;

    @Column(name = "pool_validity_message", length = 500)
    private String poolValidityMessage = "A carteirinha terá validade de 06 meses, com necessária apresentação do exame médico atualizado para validação.";

    @Column(name = "pool_show_validity_message", nullable = false)
    private Boolean poolShowValidityMessage = Boolean.TRUE;

    @Column(name = "pool_general_info", length = 500)
    private String poolGeneralInfo = "Regras da Piscina são regidas pelo Regulamento Interno e Decreto 4.447/81.";

    @Column(name = "pool_show_general_info", nullable = false)
    private Boolean poolShowGeneralInfo = Boolean.TRUE;

    @Column(name = "pool_additional_info", length = 160)
    private String poolAdditionalInfo = "Administração";

    @Column(name = "pool_card_color", nullable = false, length = 20)
    private String poolCardColor = "#0B5C2B";

    @PrePersist
    void syncTenant() {
        if (tenantId == null && tenant != null) {
            tenantId = tenant.getId();
        }
    }
}
