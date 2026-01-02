package com.example.hpadventure.providers;

/**
 * Interface for image generation providers.
 * Implementations can use different backends (OpenAI, OpenRouter, etc.)
 */
public interface ImageProvider {
    
    /**
     * Check if this provider is properly configured and ready to use.
     */
    boolean isEnabled();
    
    /**
     * Generate an image from a text prompt.
     * 
     * @param prompt The text description of the image to generate
     * @return The generated image as base64 with MIME type
     * @throws com.example.hpadventure.services.UpstreamException if generation fails
     */
    ImageResult generateImage(String prompt);
    
    /**
     * Result of image generation containing the image data.
     */
    record ImageResult(String mimeType, String base64) {
    }
}
