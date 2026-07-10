package com.example.mallordermemory.memory;

public enum MessageRole {

    USER,
    ASSISTANT,
    SYSTEM;

    public static MessageRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        return valueOf(raw.trim().toUpperCase());
    }
}
