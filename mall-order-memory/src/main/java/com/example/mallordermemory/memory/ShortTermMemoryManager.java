package com.example.mallordermemory.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 短期记忆管理器：自定义 Redis JSON 消息 + 7 天 TTL + 滑动窗口。
 */
public class ShortTermMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemoryManager.class);

    private final RedisShortTermMemoryStore store;
    private final String defaultUserId;
    private final String defaultSessionId;

    public ShortTermMemoryManager(RedisShortTermMemoryStore store,
                                  String defaultUserId,
                                  String defaultSessionId) {
        this.store = store;
        this.defaultUserId = defaultUserId;
        this.defaultSessionId = defaultSessionId;
    }

    public void addExchange(String userMessage, String assistantMessage) {
        addExchange(defaultUserId, defaultSessionId, userMessage, assistantMessage);
    }

    public void addExchange(String sessionId, String userMessage, String assistantMessage) {
        addExchange(defaultUserId, sessionId, userMessage, assistantMessage);
    }

    public void addExchange(String userId, String sessionId, String userMessage, String assistantMessage) {
        MemorySessionKey session = MemorySessionKey.of(
                resolveUserId(userId),
                resolveSessionId(sessionId));
        store.append(session, MessageRole.USER, userMessage);
        store.append(session, MessageRole.ASSISTANT, assistantMessage);
        int tokens = TokenEstimator.estimate(userMessage) + TokenEstimator.estimate(assistantMessage);
        store.addPending(session, 2, tokens);
        log.debug("Added exchange to short-term memory, session={}", session.redisKeySuffix());
    }

    public List<Message> getRecentMessages(String sessionId, int count) {
        return toSpringMessages(store.listRecent(session(sessionId), count));
    }

    public List<Message> getRecentMessages(String userId, String sessionId, int count) {
        return toSpringMessages(store.listRecent(session(userId, sessionId), count));
    }

    public List<ShortTermMessage> getRecentShortTermMessages(String userId, String sessionId, int count) {
        return store.listRecent(session(userId, sessionId), count);
    }

    public List<ShortTermMessage> listMessagesAfter(String userId, String sessionId, String afterMessageId) {
        return store.listAfter(session(userId, sessionId), afterMessageId);
    }

    public String formatConversationText(List<ShortTermMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ShortTermMessage msg : messages) {
            if (MessageRole.USER.name().equals(msg.getRole())) {
                sb.append("用户: ").append(msg.getContent()).append("\n");
            } else if (MessageRole.ASSISTANT.name().equals(msg.getRole())) {
                sb.append("助手: ").append(msg.getContent()).append("\n");
            } else {
                sb.append("系统: ").append(msg.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    public String getRecentConversation(String sessionId, int count) {
        return formatConversationText(store.listRecent(session(sessionId), count));
    }

    public int getMessageCount(String sessionId) {
        return store.messageCount(session(sessionId));
    }

    public int getMessageCount(String userId, String sessionId) {
        return store.messageCount(session(userId, sessionId));
    }

    public ConsolidationCursor getConsolidationCursor(String userId, String sessionId) {
        return store.getCursor(session(userId, sessionId));
    }

    public void saveConsolidationCursor(String userId, String sessionId, ConsolidationCursor cursor) {
        store.saveCursor(session(userId, sessionId), cursor);
    }

    public List<MemorySessionKey> listSessions() {
        return store.listSessions();
    }

    public void clear(String userId, String sessionId) {
        store.clear(session(userId, sessionId));
        log.info("Short-term memory cleared for session={}:{}", userId, sessionId);
    }

    public void clear(String sessionId) {
        clear(defaultUserId, resolveSessionId(sessionId));
    }

    public RedisShortTermMemoryStore getStore() {
        return store;
    }

    private MemorySessionKey session(String sessionId) {
        return session(defaultUserId, sessionId);
    }

    private MemorySessionKey session(String userId, String sessionId) {
        return MemorySessionKey.of(resolveUserId(userId), resolveSessionId(sessionId));
    }

    private static List<Message> toSpringMessages(List<ShortTermMessage> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (ShortTermMessage message : messages) {
            if (MessageRole.USER.name().equals(message.getRole())) {
                result.add(new UserMessage(message.getContent()));
            } else if (MessageRole.ASSISTANT.name().equals(message.getRole())) {
                result.add(new AssistantMessage(message.getContent()));
            }
        }
        return result;
    }

    private String resolveUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return defaultUserId;
        }
        return userId.trim();
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return defaultSessionId;
        }
        return sessionId.trim();
    }
}
