package com.example.hpadventure.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * A placeholder ImageProvider that always returns a static "no provider configured" image.
 * Used when no image API keys are configured.
 */
public final class PlaceholderImageProvider implements ImageProvider {
    private static final Logger logger = LoggerFactory.getLogger(PlaceholderImageProvider.class);

    private static final int IMAGE_SIZE = 1024;
    private static final String MIME_TYPE = "image/png";

    // Cached placeholder image (generated once on first use)
    private volatile String cachedBase64;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public ImageResult generateImage(String prompt) {
        logger.debug("Returning placeholder image (no provider configured)");
        return new ImageResult(MIME_TYPE, getPlaceholderBase64());
    }

    private String getPlaceholderBase64() {
        if (cachedBase64 == null) {
            synchronized (this) {
                if (cachedBase64 == null) {
                    cachedBase64 = generatePlaceholderImage();
                }
            }
        }
        return cachedBase64;
    }

    private String generatePlaceholderImage() {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            // Enable anti-aliasing
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Dark gray background
            g.setColor(new Color(45, 45, 45));
            g.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);

            // Draw border
            g.setColor(new Color(80, 80, 80));
            g.setStroke(new BasicStroke(4));
            g.drawRect(20, 20, IMAGE_SIZE - 40, IMAGE_SIZE - 40);

            // Draw diagonal cross pattern
            g.setColor(new Color(60, 60, 60));
            g.setStroke(new BasicStroke(2));
            g.drawLine(20, 20, IMAGE_SIZE - 20, IMAGE_SIZE - 20);
            g.drawLine(IMAGE_SIZE - 20, 20, 20, IMAGE_SIZE - 20);

            // Draw image icon placeholder
            int iconSize = 120;
            int iconX = (IMAGE_SIZE - iconSize) / 2;
            int iconY = IMAGE_SIZE / 2 - 100;
            g.setColor(new Color(100, 100, 100));
            g.setStroke(new BasicStroke(4));
            g.drawRect(iconX, iconY, iconSize, iconSize - 20);
            // Mountain shape inside
            int[] xPoints = {iconX + 10, iconX + iconSize / 2, iconX + iconSize - 10};
            int[] yPoints = {iconY + iconSize - 30, iconY + 30, iconY + iconSize - 30};
            g.drawPolyline(xPoints, yPoints, 3);
            // Sun
            g.drawOval(iconX + iconSize - 40, iconY + 15, 25, 25);

            // Draw text
            g.setColor(new Color(180, 180, 180));
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 36);
            g.setFont(font);
            String text = "No Image Provider";
            FontMetrics fm = g.getFontMetrics();
            int textX = (IMAGE_SIZE - fm.stringWidth(text)) / 2;
            g.drawString(text, textX, IMAGE_SIZE / 2 + 80);

            Font smallFont = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
            g.setFont(smallFont);
            g.setColor(new Color(120, 120, 120));
            String subText = "Configured";
            FontMetrics fmSmall = g.getFontMetrics();
            int subTextX = (IMAGE_SIZE - fmSmall.stringWidth(subText)) / 2;
            g.drawString(subText, subTextX, IMAGE_SIZE / 2 + 120);

        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            logger.error("Failed to generate placeholder image", e);
            // Return a minimal 1x1 transparent PNG as absolute fallback
            return "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        }
    }
}
