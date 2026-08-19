package com.css.mallorderagent.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录会话中是否存在待用户确认的敏感订单操作（对话式确认，非弹框）。
 */
@Component
public class PendingConfirmationService {

    private final ConcurrentHashMap<String, PendingConfirmation> awaiting = new ConcurrentHashMap<>();

    public void markAwaiting(String conversationId, String userId, String traceId) {
        if (conversationId != null && !conversationId.isBlank()) {
            awaiting.put(conversationId.trim(), new PendingConfirmation(
                    conversationId.trim(), userId, traceId, Instant.now()));
        }
    }

    public boolean isAwaiting(String conversationId) {
        return conversationId != null && awaiting.containsKey(conversationId.trim());
    }

    public Optional<PendingConfirmation> find(String conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(awaiting.get(conversationId.trim()));
    }

    public void clear(String conversationId) {
        if (conversationId != null) {
            awaiting.remove(conversationId.trim());
        }
    }

    public void clearAll() {
        awaiting.clear();
    }

    public record PendingConfirmation(String conversationId, String userId,
                                      String sourceTraceId, Instant createdAt) {
    }
}
