package com.packid.api.integration.whatsapp;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WhatsAppPhoneNormalizer {

    /**
     * Normaliza para formato numérico E.164 sem o sinal "+".
     * Exemplo:
     *  (21) 99888-7777 -> 5521998887777
     */
    public String normalizeBrazil(String rawPhone) {
        if (!StringUtils.hasText(rawPhone)) {
            return null;
        }

        String digits = rawPhone.replaceAll("\\D", "");

        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }

        digits = digits.replaceFirst("^0+", "");

        if (!digits.startsWith("55")) {
            digits = "55" + digits;
        }

        // Brasil: 55 + DDD(2) + número(8 ou 9)
        if (digits.length() < 12 || digits.length() > 13) {
            return null;
        }

        return digits;
    }
}