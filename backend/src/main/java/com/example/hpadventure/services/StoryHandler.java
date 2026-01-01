package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;

@FunctionalInterface
public interface StoryHandler {
    Dtos.Assistant nextTurn(Dtos.StoryRequest request);
}
