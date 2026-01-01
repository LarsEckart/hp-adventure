package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;

import java.util.function.Consumer;

public interface StoryStreamHandler {
    Dtos.Assistant streamTurn(Dtos.StoryRequest request, Consumer<String> onDelta);
}
