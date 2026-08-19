package com.css.mallorderagent.tool.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

/**
 * 通过 MCP（mall-order-cmp-server）调用订单相关 Tool，替代直连 mall-order HTTP API。
 */
@Component
public class OrderMcpToolClient {

    private static final Logger log = LoggerFactory.getLogger(OrderMcpToolClient.class);

    private final ToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper;

    public OrderMcpToolClient(ToolCallbackProvider toolCallbackProvider, ObjectMapper objectMapper) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.objectMapper = objectMapper;
    }

    public boolean cancelOrder(String orderId, String userId) {
        String result = invokeTool("cancelOrder", Map.of("orderId", orderId, "userId", userId));
        return parseBooleanResult(result);
    }

    public String submitAfterSalesRequest(String orderId, String userId, String operationType) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("orderId", orderId);
        args.put("userId", userId);
        args.put("operationType", operationType);
        return unwrapTextResult(invokeTool("submitAfterSalesRequest", args));
    }

    public String submitAddressChangeRequest(String orderId, String userId) {
        return unwrapTextResult(invokeTool(
                "submitAddressChangeRequest", Map.of("orderId", orderId, "userId", userId)));
    }

    public String evaluateRefundEligibility(String orderId,
                                            String userId,
                                            String reasonType,
                                            Boolean customerOpened,
                                            Boolean customerUsed,
                                            String conditionStatus,
                                            String reasonDescription,
                                            List<String> evidenceUrls) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("orderId", orderId);
        args.put("userId", userId);
        args.put("reasonType", reasonType != null ? reasonType : "NO_REASON");
        putIfNotNull(args, "customerOpened", customerOpened);
        putIfNotNull(args, "customerUsed", customerUsed);
        putIfNotNull(args, "conditionStatus", conditionStatus);
        putIfNotNull(args, "reasonDescription", reasonDescription);
        if (evidenceUrls != null && !evidenceUrls.isEmpty()) {
            args.put("evidenceUrls", evidenceUrls);
        }
        return unwrapTextResult(invokeTool("evaluateRefundEligibility", args));
    }

    String invokeTool(String toolName, Map<String, Object> arguments) {
        ToolCallback callback = findTool(toolName)
                .orElseThrow(() -> new OrderMcpToolException("未找到 MCP Tool: " + toolName));
        try {
            String payload = objectMapper.writeValueAsString(arguments);
            log.info("Invoking MCP tool={}, argumentKeys={}", toolName, arguments.keySet());
            return callback.call(payload);
        } catch (OrderMcpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new OrderMcpToolException("MCP Tool 调用失败: " + toolName, e);
        }
    }

    Optional<ToolCallback> findTool(String originalToolName) {
        for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
            if (callback instanceof SyncMcpToolCallback syncCallback) {
                if (originalToolName.equals(syncCallback.getOriginalToolName())) {
                    return Optional.of(callback);
                }
            }
            String registeredName = callback.getToolDefinition().name();
            if (originalToolName.equals(registeredName) || registeredName.endsWith("_" + originalToolName)) {
                return Optional.of(callback);
            }
        }
        return Optional.empty();
    }

    private boolean parseBooleanResult(String raw) {
        String text = unwrapTextResult(raw);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        try {
            return objectMapper.readValue(text, Boolean.class);
        } catch (Exception ignored) {
            log.warn("Unable to parse MCP boolean result: {}", raw);
            return false;
        }
    }

    private String unwrapTextResult(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            Map<String, Object> map = objectMapper.readValue(trimmed, new TypeReference<>() {
            });
            Object text = map.get("text");
            if (text != null) {
                return String.valueOf(text).trim();
            }
        } catch (Exception ignored) {
            // plain text response
        }
        return trimmed;
    }

    private static void putIfNotNull(Map<String, Object> arguments, String key, Object value) {
        if (value != null) {
            arguments.put(key, value);
        }
    }
}
