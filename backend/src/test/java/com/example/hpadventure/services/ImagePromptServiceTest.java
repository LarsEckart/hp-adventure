package com.example.hpadventure.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagePromptServiceTest {
    @Test
    void usesSceneWhenProvided() {
        ImagePromptService service = new ImagePromptService();

        String prompt = service.buildPrompt("Ein dunkler Korridor", "Story");

        assertTrue(prompt.contains("Ein dunkler Korridor"));
    }

    @Test
    void fallsBackToStoryWhenSceneMissing() {
        ImagePromptService service = new ImagePromptService();

        String prompt = service.buildPrompt(null, "Du stehst in der Großen Halle.\n\nWas tust du?");

        assertTrue(prompt.contains("Du stehst in der Großen Halle."));
    }
}
