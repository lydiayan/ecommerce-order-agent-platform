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

    /**
     * 向默认用户和默认会话追加一轮用户与助手消息。
     *
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    public void addExchange(String userMessage, String assistantMessage) {
        addExchange(defaultUserId, defaultSessionId, userMessage, assistantMessage);
    }

    /**
     * 向默认用户的指定会话追加一轮对话。
     *
     * @param sessionId 会话编号
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    public void addExchange(String sessionId, String userMessage, String assistantMessage) {
        addExchange(defaultUserId, sessionId, userMessage, assistantMessage);
    }

    /**
     * 向指定用户会话追加一轮对话，并累加合并所需的消息和 Token 统计。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
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

    /**
     * 读取默认用户指定会话的最近消息并转换为 Spring AI 消息。
     *
     * @param sessionId 会话编号
     * @param count 请求条数
     * @return 仅包含可识别用户和助手角色的消息
     */
    public List<Message> getRecentMessages(String sessionId, int count) {
        return toSpringMessages(store.listRecent(session(sessionId), count));
    }

    /**
     * 读取指定用户会话的最近消息并转换为 Spring AI 消息。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @param count 请求条数
     * @return 近期对话消息
     */
    public List<Message> getRecentMessages(String userId, String sessionId, int count) {
        return toSpringMessages(store.listRecent(session(userId, sessionId), count));
    }

    /**
     * 读取保留编号、角色和时间戳的原始短期消息。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @param count 请求条数
     * @return 原始短期消息
     */
    public List<ShortTermMessage> getRecentShortTermMessages(String userId, String sessionId, int count) {
        return store.listRecent(session(userId, sessionId), count);
    }

    /**
     * 读取指定合并游标之后的增量短期消息。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @param afterMessageId 上次已合并消息编号
     * @return 增量消息
     */
    public List<ShortTermMessage> listMessagesAfter(String userId, String sessionId, String afterMessageId) {
        return store.listAfter(session(userId, sessionId), afterMessageId);
    }

    /**
     * 将短期消息格式化为供记忆抽取器读取的角色标记文本。
     *
     * @param messages 短期消息
     * @return 多行对话文本
     */
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

    /**
     * 读取并格式化默认用户指定会话的最近对话。
     *
     * @param sessionId 会话编号
     * @param count 请求条数
     * @return 多行对话文本
     */
    public String getRecentConversation(String sessionId, int count) {
        return formatConversationText(store.listRecent(session(sessionId), count));
    }

    /**
     * 返回默认用户指定会话的短期消息数。
     *
     * @param sessionId 会话编号
     * @return 消息数
     */
    public int getMessageCount(String sessionId) {
        return store.messageCount(session(sessionId));
    }

    /**
     * 返回指定用户会话的短期消息数。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @return 消息数
     */
    public int getMessageCount(String userId, String sessionId) {
        return store.messageCount(session(userId, sessionId));
    }

    /**
     * 读取指定用户会话的记忆合并游标。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @return 合并游标
     */
    public ConsolidationCursor getConsolidationCursor(String userId, String sessionId) {
        return store.getCursor(session(userId, sessionId));
    }

    /**
     * 保存指定用户会话的记忆合并游标。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @param cursor 合并游标
     */
    public void saveConsolidationCursor(String userId, String sessionId, ConsolidationCursor cursor) {
        store.saveCursor(session(userId, sessionId), cursor);
    }

    /**
     * 查询当前 Redis 中的短期记忆会话。
     *
     * @return 用户与会话复合键
     */
    public List<MemorySessionKey> listSessions() {
        return store.listSessions();
    }

    /**
     * 清除指定用户会话的消息和合并游标。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     */
    public void clear(String userId, String sessionId) {
        store.clear(session(userId, sessionId));
        log.info("Short-term memory cleared for current session");
    }

    /**
     * 清除默认用户指定会话的消息和合并游标。
     *
     * @param sessionId 会话编号
     */
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
