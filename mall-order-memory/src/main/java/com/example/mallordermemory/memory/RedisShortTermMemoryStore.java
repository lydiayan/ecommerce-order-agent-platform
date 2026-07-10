package com.example.mallordermemory.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 Redis List 的短期记忆存储。
 * <p>
 * Key: {@code {keyPrefix}{userId}:{sessionId}}，每条消息为 JSON。
 * </p>
 */
public class RedisShortTermMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisShortTermMemoryStore.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final String cursorKeyPrefix;
    private final int maxSize;
    private final long ttlSeconds;

    public RedisShortTermMemoryStore(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     String keyPrefix,
                                     String cursorKeyPrefix,
                                     int maxSize,
                                     long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.cursorKeyPrefix = normalizePrefix(cursorKeyPrefix);
        this.maxSize = maxSize;
        this.ttlSeconds = ttlSeconds;
    }

    public ShortTermMessage append(MemorySessionKey session, MessageRole role, String content) {
        String redisKey = messageKey(session);
        long now = System.currentTimeMillis() / 1000;
        ShortTermMessage message = new ShortTermMessage(
                generateMessageId(),
                session.sessionId(),
                role.name(),
                content,
                now
        );
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(redisKey, json);
            trimAndExpire(redisKey);
            log.debug("Appended short-term message {} to {}", message.getMessageId(), redisKey);
            return message;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize short-term message", e);
        }
    }

    public List<ShortTermMessage> listAll(MemorySessionKey session) {
        return parseList(redisTemplate.opsForList().range(messageKey(session), 0, -1));
    }

    public List<ShortTermMessage> listRecent(MemorySessionKey session, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<String> raw = redisTemplate.opsForList().range(messageKey(session), -count, -1);
        return parseList(raw);
    }

    /**
     * 返回严格在 {@code afterMessageId} 之后的消息（不含该 ID 本身）。
     */
    public List<ShortTermMessage> listAfter(MemorySessionKey session, String afterMessageId) {
        List<ShortTermMessage> all = listAll(session);
        if (all.isEmpty()) {
            return List.of();
        }
        if (afterMessageId == null || afterMessageId.isBlank()) {
            return all;
        }
        int startIndex = -1;
        for (int i = 0; i < all.size(); i++) {
            if (afterMessageId.equals(all.get(i).getMessageId())) {
                startIndex = i + 1;
                break;
            }
        }
        if (startIndex < 0) {
            log.warn("lastMessageId={} not found in session {}, consolidating all {} messages",
                    afterMessageId, session.redisKeySuffix(), all.size());
            return all;
        }
        if (startIndex >= all.size()) {
            return List.of();
        }
        return new ArrayList<>(all.subList(startIndex, all.size()));
    }

    public int messageCount(MemorySessionKey session) {
        Long size = redisTemplate.opsForList().size(messageKey(session));
        return size != null ? size.intValue() : 0;
    }

    public void clear(MemorySessionKey session) {
        redisTemplate.delete(messageKey(session));
        redisTemplate.delete(cursorKey(session));
    }

    public List<MemorySessionKey> listSessions() {
        Set<String> keys = redisTemplate.keys(keyPrefix + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(this::parseSessionFromMessageKey)
                .filter(key -> key != null)
                .collect(Collectors.toList());
    }

    public ConsolidationCursor getCursor(MemorySessionKey session) {
        String raw = redisTemplate.opsForValue().get(cursorKey(session));
        if (raw == null || raw.isBlank()) {
            return ConsolidationCursor.empty();
        }
        try {
            return objectMapper.readValue(raw, ConsolidationCursor.class);
        } catch (JsonProcessingException e) {
            log.warn("Invalid consolidation cursor for {}: {}", session.redisKeySuffix(), e.getMessage());
            return ConsolidationCursor.empty();
        }
    }

    public void saveCursor(MemorySessionKey session, ConsolidationCursor cursor) {
        try {
            String json = objectMapper.writeValueAsString(cursor);
            String key = cursorKey(session);
            redisTemplate.opsForValue().set(key, json);
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize consolidation cursor", e);
        }
    }

    public void addPending(MemorySessionKey session, int messages, int tokens) {
        ConsolidationCursor cursor = getCursor(session);
        cursor.addPending(messages, tokens);
        saveCursor(session, cursor);
    }

    private void trimAndExpire(String redisKey) {
        Long size = redisTemplate.opsForList().size(redisKey);
        if (size != null && size > maxSize) {
            redisTemplate.opsForList().trim(redisKey, size - maxSize, -1);
        }
        redisTemplate.expire(redisKey, ttlSeconds, TimeUnit.SECONDS);
    }

    private List<ShortTermMessage> parseList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ShortTermMessage> result = new ArrayList<>(raw.size());
        for (String item : raw) {
            try {
                result.add(objectMapper.readValue(item, ShortTermMessage.class));
            } catch (JsonProcessingException e) {
                log.warn("Skip invalid short-term message json: {}", e.getMessage());
            }
        }
        return result;
    }

    private MemorySessionKey parseSessionFromMessageKey(String redisKey) {
        if (!redisKey.startsWith(keyPrefix)) {
            return null;
        }
        String suffix = redisKey.substring(keyPrefix.length());
        int split = suffix.indexOf(':');
        if (split <= 0 || split >= suffix.length() - 1) {
            return null;
        }
        return MemorySessionKey.of(suffix.substring(0, split), suffix.substring(split + 1));
    }

    private String messageKey(MemorySessionKey session) {
        return keyPrefix + session.redisKeySuffix();
    }

    private String cursorKey(MemorySessionKey session) {
        return cursorKeyPrefix + session.redisKeySuffix();
    }

    static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "memory:short:";
        }
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }

    static String generateMessageId() {
        return System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
