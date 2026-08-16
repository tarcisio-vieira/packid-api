package com.packid.api.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

@Service
public class ImageCompressionService {
    private static final long MAX_INPUT_SIZE = 12L * 1024L * 1024L;
    private static final int MAX_DIMENSION = 1280;
    private static final float JPEG_QUALITY = 0.82f;
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png");

    public ProcessedImage process(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma imagem.");
        }
        if (file.getSize() > MAX_INPUT_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A imagem deve ter no máximo 12 MB antes da compactação.");
        }
        String contentType = clean(file.getContentType());
        if (contentType == null || !ALLOWED.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use uma imagem JPG ou PNG.");
        }

        try {
            byte[] original = file.getBytes();
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo enviado não é uma imagem válida.");
            }

            int width = source.getWidth();
            int height = source.getHeight();
            double scale = Math.min(1.0, Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height));
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));

            BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) throw new IOException("Codificador JPEG indisponível.");
            ImageWriter writer = writers.next();
            try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(imageOutput);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                }
                writer.write(null, new IIOImage(target, null, null), param);
            } finally {
                writer.dispose();
            }

            String originalName = clean(file.getOriginalFilename());
            String base = originalName == null ? "foto" : originalName.replaceFirst("(?i)\\.(jpg|jpeg|png)$", "");
            base = base.replaceAll("[^A-Za-z0-9._-]", "-");
            if (base.isBlank()) base = "foto";
            return new ProcessedImage(output.toByteArray(), "image/jpeg", base + ".jpg", width, height, targetWidth, targetHeight);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível processar a imagem enviada.", ex);
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    public record ProcessedImage(byte[] bytes, String mimeType, String fileName,
                                 int originalWidth, int originalHeight, int width, int height) {}
}
