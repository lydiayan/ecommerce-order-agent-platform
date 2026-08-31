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

    /**
     * 创建并校验用户与会话复合键。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @return 去除首尾空白后的复合键
     */
    public static MemorySessionKey of(String userId, String sessionId) {
        return new MemorySessionKey(userId, sessionId);
    }

    /**
     * 生成 Redis key 使用的用户与会话后缀。
     *
     * @return {@code userId:sessionId} 格式后缀
     */
    public String redisKeySuffix() {
        return userId + ":" + sessionId;
    }
}
