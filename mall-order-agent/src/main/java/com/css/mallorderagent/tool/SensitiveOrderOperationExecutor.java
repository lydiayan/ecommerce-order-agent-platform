package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.demo.DemoCapability;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.tool.client.OrderMcpToolClient;
import com.css.mallorderagent.tool.client.OrderMcpToolException;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 人工审核通过后执行敏感订单操作（退货/退款/换货/取消等），经 MCP 调用 mall-order-cmp-server。
 */
@Component
public class SensitiveOrderOperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(SensitiveOrderOperationExecutor.class);

    private final OrderMcpToolClient orderMcpToolClient;

    public SensitiveOrderOperationExecutor(OrderMcpToolClient orderMcpToolClient) {
        this.orderMcpToolClient = orderMcpToolClient;
    }

    public SensitiveOperationResult execute(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        String toolResult = state.value(AgentGraphKeys.TOOL_RESULT, "");
        String userId = state.value(AgentGraphKeys.USER_ID, "").trim();
        String operation = HumanApprovalDetector.resolveOperationLabel(query);
        String orderId = HumanApprovalDetector.extractFirstOrderId(toolResult)
                .orElseGet(() -> OrderQueryParser.parseOrderId(query).orElse(null));

        if (AgentGraphSupport.hasCapabilityContext(state)) {
            boolean cancel = operation.contains("取消");
            String required = (cancel ? DemoCapability.ORDER_CANCEL : DemoCapability.AFTER_SALES_CREATE).name();
            if (!AgentGraphSupport.hasCapability(state, required)) {
                return SensitiveOperationResult.failure(operation, orderId, userId,
                        "当前演示身份无权执行该敏感订单操作。");
            }
        }

        if (orderId == null || orderId.isBlank()) {
            return SensitiveOperationResult.failure(operation, null, userId,
                    "未能识别订单号，无法执行" + operation + "。请提供订单号后重试。");
        }
        if (userId.isBlank()) {
            return SensitiveOperationResult.failure(operation, orderId, userId,
                    "未能识别当前用户，无法执行敏感订单操作。");
        }

        log.info("Executing sensitive operation via MCP, operation={}, orderFingerprint={}",
                operation, TracePrivacy.fingerprint(orderId));
        try {
            String message = switch (operation) {
                case "取消订单", "取消" -> executeCancel(orderId, userId);
                case "退货" -> executeAfterSales(orderId, userId, "退货");
                case "退款" -> executeAfterSales(orderId, userId, "退款");
                case "换货" -> executeAfterSales(orderId, userId, "换货");
                case "修改收货地址" -> executeAddressChange(orderId, userId);
                default -> executeAfterSales(orderId, userId, operation);
            };
            return SensitiveOperationResult.success(operation, orderId, userId, message);
        } catch (OrderMcpToolException e) {
            log.error("Sensitive operation failed via MCP, op={}, orderFingerprint={}",
                    operation, TracePrivacy.fingerprint(orderId), e);
            return SensitiveOperationResult.failure(operation, orderId, userId,
                    operation + "执行失败：订单 MCP 服务不可用（" + e.getMessage() + "）");
        }
    }

    private String executeCancel(String orderId, String userId) {
        boolean ok = orderMcpToolClient.cancelOrder(orderId, userId);
        if (ok) {
            return "已成功取消订单 " + orderId + "。";
        }
        return "取消订单 " + orderId + " 失败，请确认订单状态是否允许取消。";
    }

    private String executeAfterSales(String orderId, String userId, String operation) {
        return orderMcpToolClient.submitAfterSalesRequest(orderId, userId, operation);
    }

    private String executeAddressChange(String orderId, String userId) {
        return orderMcpToolClient.submitAddressChangeRequest(orderId, userId);
    }
}
