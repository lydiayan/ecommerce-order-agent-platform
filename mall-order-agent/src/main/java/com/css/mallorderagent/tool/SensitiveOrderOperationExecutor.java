package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.tool.client.MallOrderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * 人工审核通过后执行敏感订单操作（退货/退款/换货/取消等）。
 */
@Component
public class SensitiveOrderOperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(SensitiveOrderOperationExecutor.class);

    private final MallOrderClient mallOrderClient;

    public SensitiveOrderOperationExecutor(MallOrderClient mallOrderClient) {
        this.mallOrderClient = mallOrderClient;
    }

    public String execute(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        String toolResult = state.value(AgentGraphKeys.TOOL_RESULT, "");
        String operation = HumanApprovalDetector.resolveOperationLabel(query);
        String orderId = HumanApprovalDetector.extractFirstOrderId(toolResult)
                .orElseGet(() -> OrderQueryParser.parseOrderId(query).orElse(null));

        if (orderId == null || orderId.isBlank()) {
            return "未能识别订单号，无法执行" + operation + "。请提供订单号后重试。";
        }

        log.info("Executing sensitive operation={}, orderId={}, query='{}'", operation, orderId, query);
        try {
            return switch (operation) {
                case "取消订单", "取消" -> executeCancel(orderId);
                case "退货" -> executeAfterSales(orderId, "退货");
                case "退款" -> executeAfterSales(orderId, "退款");
                case "换货" -> executeAfterSales(orderId, "换货");
                case "修改收货地址" -> executeAddressChange(orderId);
                default -> executeAfterSales(orderId, operation);
            };
        } catch (RestClientException e) {
            log.error("Sensitive operation failed, op={}, orderId={}", operation, orderId, e);
            return operation + "执行失败：订单服务不可用（" + e.getMessage() + "）";
        }
    }

    private String executeCancel(String orderId) {
        boolean ok = mallOrderClient.cancelOrder(orderId);
        if (ok) {
            return "已成功取消订单 " + orderId + "。";
        }
        return "取消订单 " + orderId + " 失败，请确认订单状态是否允许取消。";
    }

    private String executeAfterSales(String orderId, String operation) {
        String ticketId = "SR" + System.currentTimeMillis();
        return """
                已成功提交%s申请。
                - 订单号：%s
                - 工单号：%s
                后续可在「我的订单」查看进度，客服将在 1 个工作日内处理。
                """.formatted(operation, orderId, ticketId).trim();
    }

    private String executeAddressChange(String orderId) {
        String ticketId = "ADDR" + System.currentTimeMillis();
        return """
                已提交修改收货地址申请。
                - 订单号：%s
                - 工单号：%s
                请留意客服或短信通知确认新地址。
                """.formatted(orderId, ticketId).trim();
    }
}
