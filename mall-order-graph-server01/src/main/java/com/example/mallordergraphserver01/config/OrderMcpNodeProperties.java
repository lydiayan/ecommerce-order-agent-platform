package com.example.mallordergraphserver01.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;


@ConfigurationProperties(prefix = OrderMcpNodeProperties.PREFIX)
public class OrderMcpNodeProperties {

    public static final String PREFIX = "spring.ai.graph.nodes";

    private Map<String, List<String>> node2servers;

    public Map<String, List<String>> getNode2servers() {
        return node2servers;
    }

    public void setNode2servers(Map<String, List<String>> node2servers) {
        this.node2servers = node2servers;
    }
}
