package com.example.hpadventure.services;

import java.io.OutputStream;

public interface TtsHandler {
    void stream(String text, OutputStream outputStream);
}
