package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.demo.DemoCapability;
import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.planner.executor.ActionExecutor;
import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单查询工具：调用 mall-order 服务（/orders）并将结果写入 Graph 状态。
 */
@Component("orderQueryTool")
public class OrderQueryToolExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryToolExecutor.class);
    static final String TRACE_OPERATION = "tool.order_query";
    private static final String TOOL_NAME = "ORDER_QUERY";

    private final MallOrderClient mallOrderClient;

    public OrderQueryToolExecutor(MallOrderClient mallOrderClient) {
        this.mallOrderClient = mallOrderClient;
    }

    /**
     * 根据问题中的订单号或当前身份授权客户范围查询订单，并生成可供 LLM 使用的文本结果。
     *
     * @param state 包含用户问题、身份和授权客户范围的 Graph 状态
     * @return TOOL_RESULT 与 grounded 标记
     */
    @Override
    public Map<String, Object> execute(OverAllState state) {
        RagTraceScope parentTrace = RagTracingAdvisor.parentScope();
        try (RagTraceScope toolSpan = parentTrace.child(TRACE_OPERATION)) {
            toolSpan.attribute("toolName", TOOL_NAME);
            toolSpan.attribute("executor", "orderQueryTool");
            return executeTraced(state, toolSpan);
        }
    }

    private Map<String, Object> executeTraced(OverAllState state, RagTraceScope toolSpan) {
        String query = AgentGraphSupport.resolveQuery(state);
        String stateUserId = state.value(AgentGraphKeys.USER_ID, "");
        boolean demoScoped = AgentGraphSupport.hasCapabilityContext(state);
        boolean assignedAccess = AgentGraphSupport.hasCapability(
                state, DemoCapability.ASSIGNED_ORDER_READ.name());
        List<String> authorizedCustomerIds = demoScoped
                ? AgentGraphSupport.readStringList(state, AgentGraphKeys.AUTHORIZED_CUSTOMER_IDS)
                : Optional.ofNullable(stateUserId).filter(id -> !id.isBlank())
                        .map(List::of).orElse(List.of());

        if (demoScoped && !AgentGraphSupport.hasCapability(state, DemoCapability.OWN_ORDER_READ.name())
                && !assignedAccess) {
            return failure("当前演示身份没有订单查询能力。");
        }

        log.info("OrderQueryToolExecutor invoked, userFingerprint={}, queryLength={}",
                TracePrivacy.fingerprint(stateUserId), query.length());

        try {
            Optional<String> orderId = OrderQueryParser.parseOrderId(query);
            if (orderId.isPresent()) {
                if (authorizedCustomerIds.isEmpty()) {
                    toolSpan.attribute("success", false);
                    toolSpan.attribute("failureReason", "missing_user_id");
                    return failure("查询订单详情需要提供用户 ID。");
                }
                toolSpan.attribute("parameterType", "orderId");
                toolSpan.attribute("orderFingerprint", TracePrivacy.fingerprint(orderId.get()));
                toolSpan.attribute("userFingerprint",
                        TracePrivacy.fingerprint(authorizedCustomerIds.get(0)));
                return queryByOrderId(orderId.get(), authorizedCustomerIds, assignedAccess, toolSpan);
            }

            Optional<String> queryUserId = OrderQueryParser.parseUserIdFromQuery(query).filter(id -> !id.isBlank());
            if (queryUserId.isPresent() && !authorizedCustomerIds.contains(queryUserId.get())) {
                toolSpan.attribute("success", false);
                toolSpan.attribute("failureReason", "user_scope_mismatch");
                return failure("只能查询当前演示身份已授权的客户订单。");
            }
            String userId = queryUserId.orElseGet(() -> authorizedCustomerIds.size() == 1
                    ? authorizedCustomerIds.get(0) : null);

            if (userId == null && authorizedCustomerIds.isEmpty()) {
                toolSpan.attribute("success", false);
                toolSpan.attribute("failureReason", "missing_order_or_user_id");
                return failure("未能识别用户 ID 或订单号。请在问题中提供订单号（如 ORD20260810001）或用户 ID（如 USER1001）。");
            }

            if (userId == null) {
                return queryAllAuthorizedCustomers(authorizedCustomerIds, assignedAccess, toolSpan);
            }

            toolSpan.attribute("parameterType", "userId");
            toolSpan.attribute("userFingerprint", TracePrivacy.fingerprint(userId));
            return queryByUserId(userId, assignedAccess, toolSpan);
        } catch (RestClientException e) {
            toolSpan.attribute("success", false);
            toolSpan.attribute("errorType", e.getClass().getSimpleName());
            toolSpan.error(e);
            log.error("Order query failed, queryLength={}", query.length(), e);
            return failure("订单服务调用失败，请确认 mall-order 服务已启动（默认端口 8081）。原因：" + e.getMessage());
        } catch (RuntimeException e) {
            toolSpan.attribute("success", false);
            toolSpan.attribute("errorType", e.getClass().getSimpleName());
            toolSpan.error(e);
            throw e;
        }
    }

    private Map<String, Object> queryByOrderId(String orderId, List<String> customerIds,
                                               boolean mask, RagTraceScope toolSpan) {
        MallOrderDto order = null;
        for (String customerId : customerIds) {
            try {
                order = mallOrderClient.getOrderById(orderId, customerId);
                break;
            } catch (RestClientResponseException e) {
                if (!e.getStatusCode().is4xxClientError()) {
                    throw e;
                }
            }
        }
        if (order == null) {
            return failure("未找到该订单，或当前演示身份无权查看该订单。");
        }
        if (mask) {
            order = DemoPersonaService.maskOrderForStaff(order);
        }
        String toolResult = OrderResultFormatter.formatOrder(order);
        toolSpan.attribute("success", true);
        toolSpan.attribute("resultCount", order != null ? 1 : 0);
        toolSpan.attribute("found", order != null);
        log.info("OrderQueryToolExecutor found order, orderFingerprint={}, exists={}",
                TracePrivacy.fingerprint(orderId), order != null);
        return Map.of(
                AgentGraphKeys.TOOL_RESULT, toolResult,
                AgentGraphKeys.GROUNDED, order != null);
    }

    private Map<String, Object> queryByUserId(String userId, boolean mask, RagTraceScope toolSpan) {
        List<MallOrderDto> orders = mallOrderClient.getOrdersByUserId(userId);
        if (mask) {
            orders = orders.stream().map(DemoPersonaService::maskOrderForStaff).toList();
        }
        String toolResult = OrderResultFormatter.formatOrders(orders, userId);
        toolSpan.attribute("success", true);
        toolSpan.attribute("resultCount", orders.size());
        toolSpan.attribute("found", !orders.isEmpty());
        log.info("OrderQueryToolExecutor found {} order(s), userFingerprint={}",
                orders.size(), TracePrivacy.fingerprint(userId));
        return Map.of(
                AgentGraphKeys.TOOL_RESULT, toolResult,
                AgentGraphKeys.GROUNDED, !orders.isEmpty());
    }

    private Map<String, Object> queryAllAuthorizedCustomers(List<String> customerIds,
                                                            boolean mask,
                                                            RagTraceScope toolSpan) {
        List<MallOrderDto> orders = new ArrayList<>();
        for (String customerId : customerIds) {
            List<MallOrderDto> customerOrders = mallOrderClient.getOrdersByUserId(customerId);
            orders.addAll(mask
                    ? customerOrders.stream().map(DemoPersonaService::maskOrderForStaff).toList()
                    : customerOrders);
        }
        String toolResult = OrderResultFormatter.formatOrders(orders, "已授权客户");
        toolSpan.attribute("success", true);
        toolSpan.attribute("resultCount", orders.size());
        return Map.of(
                AgentGraphKeys.TOOL_RESULT, toolResult,
                AgentGraphKeys.GROUNDED, !orders.isEmpty());
    }

    private static Map<String, Object> failure(String message) {
        return Map.of(
                AgentGraphKeys.TOOL_RESULT, message,
                AgentGraphKeys.GROUNDED, false);
    }
}
