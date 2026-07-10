package com.css.mallorderagent.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录会话中是否存在待用户确认的敏感订单操作（对话式确认，非弹框）。
 */
@Component
public class PendingConfirmationService {

    private final ConcurrentHashMap<String, Boolean> awaiting = new ConcurrentHashMap<>();

    public void markAwaiting(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            awaiting.put(conversationId.trim(), Boolean.TRUE);
        }
    }

    public boolean isAwaiting(String conversationId) {
        return conversationId != null && awaiting.containsKey(conversationId.trim());
    }

    public void clear(String conversationId) {
        if (conversationId != null) {
            awaiting.remove(conversationId.trim());
        }
    }
}
