package com.example.hpadventure.providers;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderImageProviderTest {

    @Test
    void isEnabled_returnsFalse() {
        PlaceholderImageProvider provider = new PlaceholderImageProvider();
        assertFalse(provider.isEnabled());
    }

    @Test
    void generateImage_returnsValidPng() throws Exception {
        PlaceholderImageProvider provider = new PlaceholderImageProvider();
        
        ImageProvider.ImageResult result = provider.generateImage("any prompt");
        
        assertEquals("image/png", result.mimeType());
        assertNotNull(result.base64());
        assertFalse(result.base64().isBlank());
        
        // Decode and verify it's a valid image
        byte[] imageBytes = Base64.getDecoder().decode(result.base64());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        
        assertNotNull(image);
        assertEquals(1024, image.getWidth());
        assertEquals(1024, image.getHeight());
    }

    @Test
    void generateImage_cachesSameResult() {
        PlaceholderImageProvider provider = new PlaceholderImageProvider();
        
        ImageProvider.ImageResult result1 = provider.generateImage("prompt 1");
        ImageProvider.ImageResult result2 = provider.generateImage("prompt 2");
        
        // Same base64 should be returned regardless of prompt (cached)
        assertEquals(result1.base64(), result2.base64());
    }
}
