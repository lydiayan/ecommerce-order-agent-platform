package com.css.mallorderagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单 Agent 配置。
 */
@ConfigurationProperties(prefix = "agent")
public class OrderAgentProperties {

    @NestedConfigurationProperty
    private ConversationProperties conversation = new ConversationProperties();

    @NestedConfigurationProperty
    private GraphProperties graph = new GraphProperties();

    @NestedConfigurationProperty
    private OrderProperties order = new OrderProperties();

    @NestedConfigurationProperty
    private StreamingProperties streaming = new StreamingProperties();

    @NestedConfigurationProperty
    private IntentProperties intent = new IntentProperties();

    @NestedConfigurationProperty
    private SkillProperties skill = new SkillProperties();

    public ConversationProperties getConversation() {
        return conversation;
    }

    public void setConversation(ConversationProperties conversation) {
        this.conversation = conversation != null ? conversation : new ConversationProperties();
    }

    public GraphProperties getGraph() {
        return graph;
    }

    public void setGraph(GraphProperties graph) {
        this.graph = graph != null ? graph : new GraphProperties();
    }

    public OrderProperties getOrder() {
        return order;
    }

    public void setOrder(OrderProperties order) {
        this.order = order != null ? order : new OrderProperties();
    }

    public StreamingProperties getStreaming() {
        return streaming;
    }

    public void setStreaming(StreamingProperties streaming) {
        this.streaming = streaming != null ? streaming : new StreamingProperties();
    }

    public IntentProperties getIntent() {
        return intent;
    }

    public void setIntent(IntentProperties intent) {
        this.intent = intent != null ? intent : new IntentProperties();
    }

    public SkillProperties getSkill() {
        return skill;
    }

    public void setSkill(SkillProperties skill) {
        this.skill = skill != null ? skill : new SkillProperties();
    }

    public static class SkillProperties {

        private boolean enabled = true;
        private String storageDirectory = "data/skills";
        private List<String> allowedHosts = new ArrayList<>(List.of(
                "github.com", "codeload.github.com", "gitlab.com"));
        private boolean allowPrivateHosts;
        private int connectTimeoutMillis = 5_000;
        private int readTimeoutMillis = 15_000;
        private int maxRedirects = 3;
        private long maxArchiveBytes = 10 * 1024 * 1024L;
        private long maxPackageBytes = 2 * 1024 * 1024L;
        private long maxFileBytes = 512 * 1024L;
        private int maxFiles = 200;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getStorageDirectory() { return storageDirectory; }
        public void setStorageDirectory(String storageDirectory) { this.storageDirectory = storageDirectory; }
        public List<String> getAllowedHosts() { return allowedHosts; }
        public void setAllowedHosts(List<String> allowedHosts) {
            this.allowedHosts = allowedHosts != null ? new ArrayList<>(allowedHosts) : new ArrayList<>();
        }
        public boolean isAllowPrivateHosts() { return allowPrivateHosts; }
        public void setAllowPrivateHosts(boolean allowPrivateHosts) { this.allowPrivateHosts = allowPrivateHosts; }
        public int getConnectTimeoutMillis() { return Math.max(100, connectTimeoutMillis); }
        public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
        public int getReadTimeoutMillis() { return Math.max(100, readTimeoutMillis); }
        public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
        public int getMaxRedirects() { return Math.max(0, Math.min(maxRedirects, 10)); }
        public void setMaxRedirects(int maxRedirects) { this.maxRedirects = maxRedirects; }
        public long getMaxArchiveBytes() { return Math.max(1, maxArchiveBytes); }
        public void setMaxArchiveBytes(long maxArchiveBytes) { this.maxArchiveBytes = maxArchiveBytes; }
        public long getMaxPackageBytes() { return Math.max(1, maxPackageBytes); }
        public void setMaxPackageBytes(long maxPackageBytes) { this.maxPackageBytes = maxPackageBytes; }
        public long getMaxFileBytes() { return Math.max(1, maxFileBytes); }
        public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
        public int getMaxFiles() { return Math.max(1, maxFiles); }
        public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }
    }

    public static class IntentProperties {

        /** 规则不确定时是否允许调用轻量模型。 */
        private boolean llmEnabled = true;

        private String model = "qwen-turbo";

        private double confidenceThreshold = 0.8D;

        private int maxQueryLength = 2_000;

        public boolean isLlmEnabled() {
            return llmEnabled;
        }

        public void setLlmEnabled(boolean llmEnabled) {
            this.llmEnabled = llmEnabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getConfidenceThreshold() {
            return Math.max(0D, Math.min(confidenceThreshold, 1D));
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public int getMaxQueryLength() {
            return Math.max(1, maxQueryLength);
        }

        public void setMaxQueryLength(int maxQueryLength) {
            this.maxQueryLength = maxQueryLength;
        }
    }

    public static class StreamingProperties {

        /** SSE 连接及其后端任务的最大存活时间 */
        private long timeoutMillis = 180_000;

        /** 同时存在的流式请求上限 */
        private int maxActiveStreams = 100;

        private int corePoolSize = 4;

        private int maxPoolSize = 16;

        private int queueCapacity = 64;

        public long getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        public int getMaxActiveStreams() {
            return maxActiveStreams;
        }

        public void setMaxActiveStreams(int maxActiveStreams) {
            this.maxActiveStreams = maxActiveStreams;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    public static class OrderProperties {

        /** mall-order 服务 base URL（建议用 127.0.0.1，避免 localhost 解析到 IPv6） */
        private String baseUrl = "http://127.0.0.1:8081";

        @NestedConfigurationProperty
        private McpProperties mcp = new McpProperties();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public McpProperties getMcp() {
            return mcp;
        }

        public void setMcp(McpProperties mcp) {
            this.mcp = mcp != null ? mcp : new McpProperties();
        }
    }

    public static class McpProperties {

        /** 敏感操作是否经 MCP 调用（默认 true） */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class GraphProperties {

        /** 是否启用敏感操作人工审核（Human-in-the-Loop）；开启后仅退货/付款/删除等场景触发 */
        private boolean humanReviewEnabled = false;

        public boolean isHumanReviewEnabled() {
            return humanReviewEnabled;
        }

        public void setHumanReviewEnabled(boolean humanReviewEnabled) {
            this.humanReviewEnabled = humanReviewEnabled;
        }
    }

    public static class ConversationProperties {

        /** 每个会话保留的最大轮数 */
        private int maxTurns = 10;

        public int getMaxTurns() {
            return maxTurns;
        }

        public void setMaxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
        }
    }
}
