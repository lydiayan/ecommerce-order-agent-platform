package com.example.mallordermemory.memory;

/**
 * 短期记忆会话标识：userId + sessionId。
 */
public record MemorySessionKey(String userId, String sessionId) {

    public MemorySessionKey {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        userId = userId.trim();
        sessionId = sessionId.trim();
    }

    public static MemorySessionKey of(String userId, String sessionId) {
        return new MemorySessionKey(userId, sessionId);
    }

    public String redisKeySuffix() {
        return userId + ":" + sessionId;
    }
}
