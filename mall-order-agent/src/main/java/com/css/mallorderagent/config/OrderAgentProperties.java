package com.css.mallorderagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

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
