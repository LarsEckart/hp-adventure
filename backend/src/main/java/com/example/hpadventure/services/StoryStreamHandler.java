package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;

import java.util.function.Consumer;

public interface StoryStreamHandler {
    StreamResult streamTurn(Dtos.StoryRequest request, Consumer<String> onDelta);

    Dtos.Image generateImage(String imagePrompt);

    record StreamResult(Dtos.Assistant assistant, String imagePrompt) {
    }
}
