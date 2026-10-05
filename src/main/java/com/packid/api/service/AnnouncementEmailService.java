package com.packid.api.service;

import com.packid.api.domain.model.Announcement;
import com.packid.api.domain.repository.AnnouncementRepository;
import com.packid.api.integration.google.GoogleGmailService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class AnnouncementEmailService {
    private final AnnouncementRepository announcementRepository;
    private final GoogleGmailService gmailService;

    public AnnouncementEmailService(AnnouncementRepository announcementRepository, GoogleGmailService gmailService) {
        this.announcementRepository = announcementRepository;
        this.gmailService = gmailService;
    }

    @Async("mailTaskExecutor")
    public void sendAsync(UUID announcementId, UUID tenantId, String tenantName, Set<String> recipients) {
        Announcement announcement = announcementRepository.findById(announcementId).orElse(null);
        if (announcement == null) return;

        int delivered = 0;
        int failed = 0;
        String html = wrapEmailHtml(announcement.getTitle(), announcement.getBodyHtml(), tenantName);
        String subject = "Comunicado - " + announcement.getTitle();
        for (String recipient : recipients) {
            try {
                gmailService.sendWithInlineImages(tenantId, recipient, subject, announcement.getBodyText(), html, tenantName);
                delivered++;
            } catch (Exception ex) {
                failed++;
            }
        }

        announcement.setDeliveredCount(delivered);
        announcement.setFailedCount(failed);
        announcement.setDeliveryStatus(failed == 0
                ? Announcement.DeliveryStatus.SENT
                : delivered == 0 ? Announcement.DeliveryStatus.FAILED : Announcement.DeliveryStatus.PARTIAL);
        announcement.setUpdatedBy("email-dispatch");
        announcementRepository.save(announcement);
    }

    private String wrapEmailHtml(String title, String body, String tenantName) {
        return "<div style=\"font-family:Arial,sans-serif;color:#17322c;max-width:720px;margin:auto\">"
                + "<div style=\"border-top:5px solid #0F766E;padding:20px 0 12px\"><div style=\"font-size:12px;color:#0F766E;font-weight:700;letter-spacing:.08em\">VSGI CONDOMÍNIO</div>"
                + "<h2 style=\"margin:8px 0 4px\">" + escape(title) + "</h2><div style=\"color:#667771;font-size:13px\">" + escape(tenantName) + "</div></div>"
                + "<div style=\"padding:8px 0 24px;line-height:1.55\">" + body + "</div>"
                + "<div style=\"border-top:1px solid #dce8e4;padding-top:12px;color:#82918c;font-size:12px\">Mensagem enviada pelo VSGI Condomínio.</div></div>";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
