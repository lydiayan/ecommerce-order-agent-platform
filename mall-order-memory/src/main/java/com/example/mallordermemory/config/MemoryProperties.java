package com.example.mallordermemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 记忆模块配置属性
 * <p>
 * 前缀 {@code memory}.
 * </p>
 */
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    /** 是否启用记忆模块（默认 true） */
    private boolean enabled = true;

    /** 默认用户 ID */
    private String userId = "default_user";

    /** 默认对话 ID（sessionId） */
    private String conversationId = "default_conversation";

    /** 短期记忆配置 */
    @NestedConfigurationProperty
    private ShortTermProperties shortTerm = new ShortTermProperties();

    /** 长期记忆配置 */
    @NestedConfigurationProperty
    private LongTermProperties longTerm = new LongTermProperties();

    /** 合并服务配置 */
    @NestedConfigurationProperty
    private ConsolidationProperties consolidation = new ConsolidationProperties();

    /** 提取器配置 */
    @NestedConfigurationProperty
    private ExtractorProperties extractor = new ExtractorProperties();

    /** 用户画像（MySQL）配置 */
    @NestedConfigurationProperty
    private UserProfileProperties userProfile = new UserProfileProperties();

    // --- getters / setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public ShortTermProperties getShortTerm() { return shortTerm; }
    public void setShortTerm(ShortTermProperties shortTerm) { this.shortTerm = shortTerm; }

    public LongTermProperties getLongTerm() { return longTerm; }
    public void setLongTerm(LongTermProperties longTerm) { this.longTerm = longTerm; }

    public ConsolidationProperties getConsolidation() { return consolidation; }
    public void setConsolidation(ConsolidationProperties consolidation) { this.consolidation = consolidation; }

    public ExtractorProperties getExtractor() { return extractor; }
    public void setExtractor(ExtractorProperties extractor) { this.extractor = extractor; }

    public UserProfileProperties getUserProfile() { return userProfile; }
    public void setUserProfile(UserProfileProperties userProfile) { this.userProfile = userProfile; }

    // ==================== 嵌套配置类 ====================

    public static class ShortTermProperties {
        /** 短期记忆最大消息数 */
        private int maxSize = 20;

        /** Redis key 前缀，完整 key 为 {keyPrefix}:{userId}:{sessionId} */
        private String keyPrefix = "memory:short";

        /** 合并游标 Redis key 前缀 */
        private String cursorKeyPrefix = "memory:consolidate:cursor";

        /** 短期记忆过期时间（秒），默认 7 天 */
        private long ttlSeconds = 604_800L;

        @NestedConfigurationProperty
        private RedisProperties redis = new RedisProperties();

        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
        public String getCursorKeyPrefix() { return cursorKeyPrefix; }
        public void setCursorKeyPrefix(String cursorKeyPrefix) { this.cursorKeyPrefix = cursorKeyPrefix; }
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public RedisProperties getRedis() { return redis; }
        public void setRedis(RedisProperties redis) { this.redis = redis != null ? redis : new RedisProperties(); }

        public static class RedisProperties {
            private String host = "localhost";
            private int port = 6379;
            /** Lettuce 连接/命令超时（毫秒） */
            private int connectionTimeoutMs = 5000;

            public String getHost() { return host; }
            public void setHost(String host) { this.host = host; }
            public int getPort() { return port; }
            public void setPort(int port) { this.port = port; }
            public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
            public void setConnectionTimeoutMs(int connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
        }
    }

    public static class LongTermProperties {
        /** Milvus 连接配置 */
        private MilvusProperties milvus = new MilvusProperties();

        /** 向量维度（需与 embedding 模型匹配） */
        private int dimension = 1536;

        public MilvusProperties getMilvus() { return milvus; }
        public void setMilvus(MilvusProperties milvus) { this.milvus = milvus; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }

        public static class MilvusProperties {
            private String host = "localhost";
            private int port = 19530;

            public String getHost() { return host; }
            public void setHost(String host) { this.host = host; }
            public int getPort() { return port; }
            public void setPort(int port) { this.port = port; }
        }
    }

    public static class ConsolidationProperties {
        /** 是否启用定时合并 */
        private boolean enabled = true;

        /** 合并间隔（毫秒），默认 5 分钟 */
        private long intervalMs = 300_000L;

        /** 会话至少有多少条消息才允许合并 */
        private int minMessages = 4;

        /** 增量消息条数达到该值触发合并 */
        private int messageThreshold = 10;

        /** 增量 Token 达到该值触发合并 */
        private int tokenThreshold = 800;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public int getMinMessages() { return minMessages; }
        public void setMinMessages(int minMessages) { this.minMessages = minMessages; }
        public int getMessageThreshold() { return messageThreshold; }
        public void setMessageThreshold(int messageThreshold) { this.messageThreshold = messageThreshold; }
        public int getTokenThreshold() { return tokenThreshold; }
        public void setTokenThreshold(int tokenThreshold) { this.tokenThreshold = tokenThreshold; }
    }

    public static class ExtractorProperties {
        /** 是否启用 LLM 提取 */
        private boolean enabled = true;

        /** 规则层使用的 LLM 模型名称 */
        private String model = "qwen-max";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class UserProfileProperties {
        /** 是否启用 MySQL 用户画像 */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
