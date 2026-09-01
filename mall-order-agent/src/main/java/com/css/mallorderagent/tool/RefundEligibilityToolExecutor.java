package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.demo.DemoCapability;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.executor.ActionExecutor;
import com.css.mallorderagent.tool.client.OrderMcpToolClient;
import com.css.mallorderagent.tool.client.OrderMcpToolException;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component("refundEligibilityTool")
public class RefundEligibilityToolExecutor implements ActionExecutor {

    static final String TRACE_OPERATION = "tool.refund_eligibility";
    private static final Logger log = LoggerFactory.getLogger(RefundEligibilityToolExecutor.class);

    private final OrderMcpToolClient orderMcpToolClient;

    public RefundEligibilityToolExecutor(OrderMcpToolClient orderMcpToolClient) {
        this.orderMcpToolClient = orderMcpToolClient;
    }

    /**
     * 解析退款事实并通过 MCP 对授权范围内的订单执行资格评估。
     *
     * @param state 包含问题、订单号和授权客户范围的 Graph 状态
     * @return 权威资格判断文本与 grounded 标记
     */
    @Override
    public Map<String, Object> execute(OverAllState state) {
        RagTraceScope parentTrace = RagTracingAdvisor.parentScope();
        try (RagTraceScope span = parentTrace.child(TRACE_OPERATION)) {
            return executeTraced(state, span);
        }
    }

    private Map<String, Object> executeTraced(OverAllState state, RagTraceScope span) {
        String query = AgentGraphSupport.resolveQuery(state);
        Optional<String> orderId = OrderQueryParser.parseOrderId(query);
        if (orderId.isEmpty()) {
            return failure("判断退款资格需要提供订单号。");
        }

        boolean demoScoped = AgentGraphSupport.hasCapabilityContext(state);
        boolean assignedAccess = AgentGraphSupport.hasCapability(
                state, DemoCapability.ASSIGNED_ORDER_READ.name());
        if (demoScoped && !AgentGraphSupport.hasCapability(state, DemoCapability.OWN_ORDER_READ.name())
                && !assignedAccess) {
            return failure("当前演示身份没有订单查询能力。");
        }

        String stateUserId = state.value(AgentGraphKeys.USER_ID, "");
        List<String> authorizedCustomerIds = demoScoped
                ? AgentGraphSupport.readStringList(state, AgentGraphKeys.AUTHORIZED_CUSTOMER_IDS)
                : Optional.ofNullable(stateUserId).filter(id -> !id.isBlank()).map(List::of).orElse(List.of());
        if (authorizedCustomerIds.isEmpty()) {
            return failure("判断退款资格需要提供用户 ID。");
        }

        RefundEligibilityQueryParser.RefundQueryContext context = RefundEligibilityQueryParser.parse(query);
        span.attribute("orderFingerprint", TracePrivacy.fingerprint(orderId.get()));
        span.attribute("reasonType", context.reasonType());

        OrderMcpToolException lastFailure = null;
        for (String customerId : authorizedCustomerIds) {
            try {
                String result = orderMcpToolClient.evaluateRefundEligibility(
                        orderId.get(), customerId, context.reasonType(), context.customerOpened(),
                        context.customerUsed(), context.conditionStatus(), context.reasonDescription(),
                        context.evidenceUrls());
                span.attribute("success", true);
                span.attribute("userFingerprint", TracePrivacy.fingerprint(customerId));
                return Map.of(AgentGraphKeys.TOOL_RESULT, result, AgentGraphKeys.GROUNDED, true);
            } catch (OrderMcpToolException e) {
                lastFailure = e;
            }
        }

        span.attribute("success", false);
        if (lastFailure != null) {
            log.warn("Refund eligibility MCP call failed, orderFingerprint={}: {}",
                    TracePrivacy.fingerprint(orderId.get()), lastFailure.getMessage());
        }
        return failure("未找到该订单，或当前演示身份无权判断该订单的退款资格。");
    }

    private static Map<String, Object> failure(String message) {
        return Map.of(AgentGraphKeys.TOOL_RESULT, message, AgentGraphKeys.GROUNDED, false);
    }
}
