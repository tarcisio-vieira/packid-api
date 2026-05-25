package com.packid.api.service;

import com.packid.api.domain.model.PackId;
import com.packid.api.domain.model.Person;
import com.packid.api.domain.repository.PackIdRepository;
import com.packid.api.domain.repository.PersonRepository;
import com.packid.api.integration.whatsapp.WhatsAppClient;
import com.packid.api.integration.whatsapp.WhatsAppPhoneNormalizer;
import com.packid.api.integration.whatsapp.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Component
public class PackIdWhatsAppNotifier {

    private static final Logger log = LoggerFactory.getLogger(PackIdWhatsAppNotifier.class);

    private final PackIdRepository packIdRepository;
    private final PersonRepository personRepository;
    private final WhatsAppClient whatsAppClient;
    private final WhatsAppPhoneNormalizer phoneNormalizer;
    private final WhatsAppProperties properties;

    public PackIdWhatsAppNotifier(
            PackIdRepository packIdRepository,
            PersonRepository personRepository,
            WhatsAppClient whatsAppClient,
            WhatsAppPhoneNormalizer phoneNormalizer,
            WhatsAppProperties properties
    ) {
        this.packIdRepository = packIdRepository;
        this.personRepository = personRepository;
        this.whatsAppClient = whatsAppClient;
        this.phoneNormalizer = phoneNormalizer;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPackIdCreated(PackIdCreatedEvent event) {
        if (!properties.isEnabled()) {
            log.info("WhatsApp desabilitado. PackId {} não notificará morador.", event.packIdId());
            return;
        }

        PackId packId = packIdRepository
                .findByTenantIdAndIdAndDeletedFalse(event.tenantId(), event.packIdId())
                .orElse(null);

        if (packId == null) {
            log.warn("PackId {} não encontrado após commit.", event.packIdId());
            return;
        }

        if (packId.getWhatsappSentAt() != null) {
            log.info(
                    "PackId {} já possui envio WhatsApp registrado. whatsappSentAt={}, whatsappMessageId={}",
                    packId.getId(),
                    packId.getWhatsappSentAt(),
                    packId.getWhatsappMessageId()
            );
            return;
        }

        Person person = personRepository
                .findByTenantIdAndIdAndDeletedFalse(packId.getTenantId(), packId.getPersonId())
                .orElse(null);

        if (person == null) {
            log.warn(
                    "Pessoa {} não encontrada para PackId {}.",
                    packId.getPersonId(),
                    packId.getId()
            );
            return;
        }

        String originalPhone = person.getPhone();
        String normalizedPhone = phoneNormalizer.normalizeBrazil(originalPhone);

        log.info(
                "Preparando envio WhatsApp. packId={}, tenantId={}, personId={}, personName='{}', telefoneOriginal='{}', telefoneNormalizado='{}'",
                packId.getId(),
                packId.getTenantId(),
                person.getId(),
                person.getFullName(),
                originalPhone,
                normalizedPhone
        );

        if (normalizedPhone == null) {
            log.warn(
                    "Pessoa {} sem telefone válido para WhatsApp. Telefone original: {}",
                    person.getId(),
                    originalPhone
            );
            return;
        }

        try {
            String messageId = whatsAppClient.sendArrivalTemplate(normalizedPhone);

            packId.setWhatsappMessageId(messageId);
            packId.setWhatsappSentAt(LocalDateTime.now());
            packIdRepository.save(packId);

            log.info(
                    "WhatsApp aceito pela Meta com sucesso. packId={}, personId={}, telefoneEnviado='{}', messageId={}",
                    packId.getId(),
                    person.getId(),
                    normalizedPhone,
                    messageId
            );
        } catch (Exception ex) {
            log.error(
                    "Erro ao enviar WhatsApp do PackId {} para personId {} no telefone '{}': {}",
                    packId.getId(),
                    person.getId(),
                    normalizedPhone,
                    ex.getMessage(),
                    ex
            );
        }
    }
}