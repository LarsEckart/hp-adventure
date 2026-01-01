package com.example.hpadventure.services;

public final class UpstreamException extends RuntimeException {
    private final String code;
    private final int status;

    public UpstreamException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public UpstreamException(String code, int status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
