package com.css.mallorderagent.tool.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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

    /**
     * 调用 cancelOrder MCP Tool 取消用户拥有的订单。
     *
     * @param orderId 待取消订单编号
     * @param userId 订单所属用户编号
     * @return Tool 返回的取消结果
     */
    public boolean cancelOrder(String orderId, String userId) {
        String result = invokeTool("cancelOrder", Map.of("orderId", orderId, "userId", userId));
        return parseBooleanResult(result);
    }

    /**
     * 调用售后 MCP Tool 提交退款、退货或换货申请。
     *
     * @param orderId 申请售后的订单编号
     * @param userId 订单所属用户编号
     * @param operationType 退款、退货或换货操作类型
     * @return 区分成功和业务拒绝的结构化 Tool 结果
     */
    public AfterSalesToolResult submitAfterSalesRequest(String orderId, String userId, String operationType) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("orderId", orderId);
        args.put("userId", userId);
        args.put("operationType", operationType);
        return parseAfterSalesResult(invokeTool("submitAfterSalesRequest", args));
    }

    /**
     * 调用地址变更 MCP Tool 为指定订单提交修改请求。
     *
     * @param orderId 待修改地址的订单编号
     * @param userId 订单所属用户编号
     * @return Tool 返回的业务结果文本
     */
    public String submitAddressChangeRequest(String orderId, String userId) {
        return unwrapTextResult(invokeTool(
                "submitAddressChangeRequest", Map.of("orderId", orderId, "userId", userId)));
    }

    /**
     * 调用退款资格 MCP Tool，并只传递已经解析出的可选事实。
     *
     * @param orderId 待评估订单编号
     * @param userId 订单所属用户编号
     * @param reasonType 退款原因类型，缺省时使用 NO_REASON
     * @param customerOpened 商品是否已拆封
     * @param customerUsed 商品是否已使用
     * @param conditionStatus 商品当前状态
     * @param reasonDescription 用户补充原因
     * @param evidenceUrls 售后凭证地址
     * @return Tool 返回的结构化资格判断文本
     */
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
        try {
            ToolCallback callback = findTool(toolName)
                    .orElseThrow(() -> new OrderMcpToolException("未找到 MCP Tool: " + toolName));
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
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            String extracted = extractText(node);
            if (extracted != null) {
                return extracted;
            }
        } catch (Exception ignored) {
            // plain text response
        }
        return trimmed;
    }

    /**
     * Spring AI serializes MCP content as an array of content blocks. Handle
     * that shape as well as the object/string wrappers used by older clients.
     */
    private String extractText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return unwrapTextResult(node.textValue());
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = extractText(item);
                if (text != null) {
                    return text;
                }
            }
            return null;
        }
        if (node.isObject()) {
            JsonNode text = node.get("text");
            if (text != null) {
                return extractText(text);
            }
            JsonNode data = node.get("data");
            if (data != null) {
                return extractText(data);
            }
        }
        return null;
    }

    private AfterSalesToolResult parseAfterSalesResult(String raw) {
        String text = unwrapTextResult(raw);
        JsonNode resultNode;
        try {
            resultNode = objectMapper.readTree(text);
        } catch (Exception ignored) {
            return AfterSalesToolResult.legacySuccess(text);
        }
        if (resultNode == null || !resultNode.isObject()) {
            throw new OrderMcpToolException("MCP Tool 返回了无效的售后结果结构");
        }
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(text, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new OrderMcpToolException("MCP Tool 返回了无效的售后结果", exception);
        }
        if (map.containsKey("success")) {
            try {
                return objectMapper.convertValue(map, AfterSalesToolResult.class);
            } catch (IllegalArgumentException exception) {
                throw new OrderMcpToolException("MCP Tool 返回了无效的售后结果", exception);
            }
        }
        throw new OrderMcpToolException("MCP Tool 返回的售后结果缺少 success 字段");
    }

    private static void putIfNotNull(Map<String, Object> arguments, String key, Object value) {
        if (value != null) {
            arguments.put(key, value);
        }
    }
}
