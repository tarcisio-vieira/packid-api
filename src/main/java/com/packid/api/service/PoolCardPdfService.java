package com.packid.api.service;

import com.packid.api.controller.pool.dto.PoolCardSettingsResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.PoolCard;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import jakarta.transaction.Transactional;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PoolCardPdfService {
    private static final int WIDTH = 1200;
    private static final int HEIGHT = 760;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PoolCardService poolCardService;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final CondominiumBrandingService brandingService;

    public PoolCardPdfService(PoolCardService poolCardService,
                              AuthenticatedUserService authenticatedUserService,
                              AccessControlService accessControlService,
                              CondominiumBrandingService brandingService) {
        this.poolCardService = poolCardService;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.brandingService = brandingService;
    }

    @Transactional
    public byte[] pdf(OidcUser principal, UUID id) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requirePoolCardViewer(user);
        PoolCard card = poolCardService.require(user.getTenantId(), id);
        if (card.getReviewStatus() != PoolCard.ReviewStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A carteirinha ainda não foi validada pela administração.");
        }
        if (card.getValidUntil() == null || card.getValidUntil().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A carteirinha está vencida.");
        }
        return render(card);
    }

    public byte[] render(PoolCard card) {
        try {
            PoolCardSettingsResponse settings = poolCardService.settingsForTenant(card.getTenantId());
            BufferedImage image = drawCard(card, settings);
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", jpeg);
            return jpegToPdf(jpeg.toByteArray(), WIDTH, HEIGHT);
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível gerar o PDF da carteirinha.", ex);
        }
    }

    private BufferedImage drawCard(PoolCard card, PoolCardSettingsResponse s) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        Color accent = parseColor(s.color());
        int m = 22;
        g.setStroke(new BasicStroke(6f));
        g.setColor(accent);
        g.drawRoundRect(m, m, WIDTH - 2 * m, HEIGHT - 2 * m, 42, 42);

        // Ondas inferiores, inspiradas no cartão de referência.
        Path2D waveLight = new Path2D.Double();
        waveLight.moveTo(m, 560);
        waveLight.curveTo(250, 505, 415, 555, 600, 625);
        waveLight.curveTo(800, 700, 960, 690, WIDTH - m, 655);
        waveLight.lineTo(WIDTH - m, HEIGHT - m);
        waveLight.lineTo(m, HEIGHT - m);
        waveLight.closePath();
        g.setColor(mix(accent, Color.WHITE, .24f));
        g.fill(waveLight);

        Path2D wave = new Path2D.Double();
        wave.moveTo(m, 595);
        wave.curveTo(230, 535, 390, 595, 575, 655);
        wave.curveTo(780, 725, 1005, 714, WIDTH - m, 680);
        wave.lineTo(WIDTH - m, HEIGHT - m);
        wave.lineTo(m, HEIGHT - m);
        wave.closePath();
        g.setColor(accent);
        g.fill(wave);

        // Separador central.
        g.setStroke(new BasicStroke(2f));
        g.setColor(accent.darker());
        g.drawLine(680, 225, 680, 575);

        drawLogo(g, card.getTenantId(), 75, 75, 185, 130, accent);

        RegistryEntry resident = card.getResident();
        String name = resident == null ? "" : safe(resident.getName());
        String block = resident == null ? "" : safe(resident.getBlock());
        String apartment = resident == null ? "" : safe(resident.getApartment());

        g.setColor(new Color(28, 28, 28));
        font(g, Font.BOLD, 28);
        drawField(g, "Bloco:", block, 78, 260, 170);
        drawField(g, "Apto:", apartment, 345, 260, 170);
        drawField(g, "Nome:", name, 78, 330, 490);
        drawField(g, "Emissão:", card.getIssueDate() == null ? "" : DATE.format(card.getIssueDate()), 78, 400, 230);

        font(g, Font.BOLD, 24);
        g.drawString("Validade:", 365, 400);
        font(g, Font.PLAIN, 24);
        g.drawString(card.getValidUntil() == null ? "" : DATE.format(card.getValidUntil()), 475, 400);

        font(g, Font.BOLD, 24);
        g.drawString("Menor de 10 anos:", 78, 470);
        drawCheckbox(g, 300, 447, Boolean.TRUE.equals(card.getUnderTen()), "Sim");
        drawCheckbox(g, 430, 447, !Boolean.TRUE.equals(card.getUnderTen()), "Não");

        // Cabeçalho direito.
        g.setColor(accent.darker());
        font(g, Font.BOLD, 62);
        drawCentered(g, safeDefault(s.title(), "PISCINA"), 920, 105);
        g.setColor(new Color(30, 30, 30));
        font(g, Font.PLAIN, 38);
        drawCentered(g, safeDefault(s.subtitle(), "USO DA PISCINA"), 920, 155);

        int y = 225;
        if (s.showOpeningHours() && !blank(s.openingHours())) {
            y = drawInfo(g, "◷", "Horário de funcionamento:", s.openingHours(), 725, y, accent);
        }
        if (s.showClosedDays() && !blank(s.closedDaysMessage())) {
            y = drawInfo(g, "●", "", s.closedDaysMessage(), 725, y, accent);
        }
        if (s.showValidityMessage() && !blank(s.validityMessage())) {
            y = drawInfo(g, "▣", "", s.validityMessage(), 725, y, accent);
        }
        if (s.showGeneralInfo() && !blank(s.generalInfo())) {
            g.setColor(new Color(110, 110, 110));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4f, 5f}, 0));
            g.drawLine(720, Math.min(y + 5, 535), 1120, Math.min(y + 5, 535));
            font(g, Font.PLAIN, 17);
            g.setColor(new Color(55,55,55));
            drawWrapped(g, s.generalInfo(), 725, Math.min(y + 36, 565), 390, 22, 2);
        }
        if (!blank(s.additionalInfo())) {
            font(g, Font.PLAIN, 17);
            g.setColor(new Color(80,80,80));
            drawRight(g, s.additionalInfo(), 1115, 575);
        }

        drawSwimmer(g, 145, 665, Color.WHITE);
        g.dispose();
        return img;
    }

    private void drawLogo(Graphics2D g, UUID tenantId, int x, int y, int w, int h, Color accent) {
        try {
            GoogleDrivePhotoService.PhotoContent p = brandingService.downloadForTenant(tenantId);
            BufferedImage logo = ImageIO.read(new ByteArrayInputStream(p.bytes()));
            if (logo != null) {
                double scale = Math.min((double) w / logo.getWidth(), (double) h / logo.getHeight());
                int dw = (int) (logo.getWidth() * scale), dh = (int) (logo.getHeight() * scale);
                g.drawImage(logo, x + (w-dw)/2, y + (h-dh)/2, dw, dh, null);
                return;
            }
        } catch (Exception ignored) { }
        g.setColor(accent);
        g.fillRoundRect(x, y, w, h, 18, 18);
        g.setColor(Color.WHITE);
        font(g, Font.BOLD, 25);
        drawCentered(g, "CONDOMÍNIO", x + w/2, y + 62);
    }

    private int drawInfo(Graphics2D g, String icon, String label, String text, int x, int y, Color accent) {
        g.setColor(accent.darker());
        font(g, Font.BOLD, 28);
        g.drawString(icon, x, y + 7);
        int tx = x + 45;
        if (!blank(label)) {
            font(g, Font.BOLD, 18);
            g.drawString(label, tx, y);
            y += 22;
        }
        g.setColor(new Color(45,45,45));
        font(g, Font.PLAIN, 17);
        int lines = drawWrapped(g, text, tx, y + 2, 350, 22, 3);
        return y + Math.max(52, lines * 22 + 22);
    }

    private void drawField(Graphics2D g, String label, String value, int x, int y, int width) {
        font(g, Font.BOLD, 24);
        g.drawString(label, x, y);
        int lx = x + g.getFontMetrics().stringWidth(label) + 12;
        font(g, Font.PLAIN, 24);
        String fitted = fit(g, value, width);
        g.drawString(fitted, lx, y);
        g.setColor(new Color(145,145,145));
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(lx, y + 8, lx + width, y + 8);
        g.setColor(new Color(28,28,28));
    }

    private void drawCheckbox(Graphics2D g, int x, int y, boolean selected, String label) {
        font(g, Font.PLAIN, 22);
        g.setColor(new Color(35,35,35));
        g.drawString(label, x, y + 22);
        int bx = x + 50;
        g.drawRoundRect(bx, y, 28, 28, 7, 7);
        if (selected) {
            g.setStroke(new BasicStroke(3f));
            g.drawLine(bx + 6, y + 14, bx + 12, y + 21);
            g.drawLine(bx + 12, y + 21, bx + 23, y + 7);
        }
    }

    private void drawSwimmer(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.fillOval(x + 65, y - 75, 34, 34);
        g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(x + 5, y - 42, 115, 62, 15, 150);
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(x, y - 12, 140, 34, 185, 170);
        g.drawArc(x, y + 5, 140, 34, 185, 170);
    }

    private int drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight, int maxLines) {
        if (blank(text)) return 0;
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String next = current.length() == 0 ? word : current + " " + word;
            if (g.getFontMetrics().stringWidth(next) <= maxWidth) current = new StringBuilder(next);
            else {
                if (current.length() > 0) lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        int count = Math.min(lines.size(), maxLines);
        for (int i=0; i<count; i++) {
            String line = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) line = fit(g, line + "…", maxWidth);
            g.drawString(line, x, y + i * lineHeight);
        }
        return count;
    }

    private String fit(Graphics2D g, String value, int width) {
        String s = safe(value);
        if (g.getFontMetrics().stringWidth(s) <= width) return s;
        while (s.length() > 2 && g.getFontMetrics().stringWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int y) {
        g.drawString(text, centerX - g.getFontMetrics().stringWidth(text)/2, y);
    }
    private void drawRight(Graphics2D g, String text, int rightX, int y) { g.drawString(text, rightX - g.getFontMetrics().stringWidth(text), y); }
    private void font(Graphics2D g, int style, int size) { g.setFont(new Font(Font.SANS_SERIF, style, size)); }
    private Color parseColor(String v) { try { return Color.decode(v); } catch (Exception e) { return new Color(11,92,43); } }
    private Color mix(Color a, Color b, float amount) {
        return new Color((int)(a.getRed()*(1-amount)+b.getRed()*amount), (int)(a.getGreen()*(1-amount)+b.getGreen()*amount), (int)(a.getBlue()*(1-amount)+b.getBlue()*amount));
    }
    private String safe(String v) { return v == null ? "" : v; }
    private String safeDefault(String v, String d) { return blank(v) ? d : v.trim(); }
    private boolean blank(String v) { return v == null || v.isBlank(); }

    private byte[] jpegToPdf(byte[] jpeg, int width, int height) throws Exception {
        // Página no tamanho físico aproximado de um cartão padrão (85,6 mm de largura),
        // para que a exportação possa ser impressa sem sair em tamanho de página inteira.
        double pageWidth = 85.6d * 72d / 25.4d;
        double pageHeight = pageWidth * height / width;
        String pageWidthText = String.format(java.util.Locale.ROOT, "%.2f", pageWidth);
        String pageHeightText = String.format(java.util.Locale.ROOT, "%.2f", pageHeight);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        offsets.add(out.size());
        write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets.add(out.size());
        write(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        offsets.add(out.size());
        write(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pageWidthText + " " + pageHeightText + "] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");
        offsets.add(out.size());
        write(out, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.length + " >>\nstream\n");
        out.write(jpeg);
        write(out, "\nendstream\nendobj\n");
        byte[] content = ("q\n" + pageWidthText + " 0 0 " + pageHeightText + " 0 0 cm\n/Im0 Do\nQ\n").getBytes(StandardCharsets.US_ASCII);
        offsets.add(out.size());
        write(out, "5 0 obj\n<< /Length " + content.length + " >>\nstream\n");
        out.write(content);
        write(out, "endstream\nendobj\n");
        int xref = out.size();
        write(out, "xref\n0 6\n0000000000 65535 f \n");
        for (int offset : offsets) write(out, String.format("%010d 00000 n \n", offset));
        write(out, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }
    private void write(ByteArrayOutputStream out, String text) throws Exception { out.write(text.getBytes(StandardCharsets.US_ASCII)); }
}
