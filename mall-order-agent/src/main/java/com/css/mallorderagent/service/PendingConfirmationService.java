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

    /**
     * 记录一个等待用户确认的敏感操作；相同会话编号会覆盖旧记录。
     *
     * @param conversationId Graph 线程或会话编号
     * @param userId 待确认操作所属的业务用户编号
     * @param traceId 触发本次确认的第一阶段 Trace ID
     */
    public void markAwaiting(String conversationId, String userId, String traceId) {
        if (conversationId != null && !conversationId.isBlank()) {
            awaiting.put(conversationId.trim(), new PendingConfirmation(
                    conversationId.trim(), userId, traceId, Instant.now()));
        }
    }

    /**
     * 判断指定会话是否存在等待确认的敏感操作。
     *
     * @param conversationId Graph 线程或会话编号
     * @return 存在待确认记录时返回 true
     */
    public boolean isAwaiting(String conversationId) {
        return conversationId != null && awaiting.containsKey(conversationId.trim());
    }

    /**
     * 查询指定会话的待确认记录。
     *
     * @param conversationId Graph 线程或会话编号
     * @return 待确认记录；会话编号为空或记录不存在时返回 empty
     */
    public Optional<PendingConfirmation> find(String conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(awaiting.get(conversationId.trim()));
    }

    /**
     * 清除指定会话的待确认状态。
     *
     * @param conversationId Graph 线程或会话编号
     */
    public void clear(String conversationId) {
        if (conversationId != null) {
            awaiting.remove(conversationId.trim());
        }
    }

    /** 清除全部待确认状态，主要用于重置演示环境。 */
    public void clearAll() {
        awaiting.clear();
    }

    public record PendingConfirmation(String conversationId, String userId,
                                      String sourceTraceId, Instant createdAt) {
    }
}
