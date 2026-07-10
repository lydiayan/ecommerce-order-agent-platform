package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.executor.ActionExecutor;
import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单查询工具：调用 mall-order 服务（/orders）并将结果写入 Graph 状态。
 */
@Component("orderQueryTool")
public class OrderQueryToolExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryToolExecutor.class);

    private final MallOrderClient mallOrderClient;

    public OrderQueryToolExecutor(MallOrderClient mallOrderClient) {
        this.mallOrderClient = mallOrderClient;
    }

    @Override
    public Map<String, Object> execute(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        String stateUserId = state.value(AgentGraphKeys.USER_ID, "");

        log.info("OrderQueryToolExecutor invoked, userId={}, query='{}'", stateUserId, query);

        try {
            Optional<String> orderId = OrderQueryParser.parseOrderId(query);
            if (orderId.isPresent()) {
                return queryByOrderId(orderId.get());
            }

            String userId = OrderQueryParser.parseUserIdFromQuery(query)
                    .filter(id -> !id.isBlank())
                    .or(() -> Optional.ofNullable(stateUserId).filter(id -> !id.isBlank()))
                    .orElse(null);

            if (userId == null) {
                return failure("未能识别用户 ID 或订单号。请在问题中提供订单号（如 ORD20250101120000）或用户 ID（如 USER1005）。");
            }

            return queryByUserId(userId);
        } catch (RestClientException e) {
            log.error("Order query failed, query='{}'", query, e);
            return failure("订单服务调用失败，请确认 mall-order 服务已启动（默认端口 8081）。原因：" + e.getMessage());
        }
    }

    private Map<String, Object> queryByOrderId(String orderId) {
        MallOrderDto order = mallOrderClient.getOrderById(orderId);
        String toolResult = OrderResultFormatter.formatOrder(order);
        log.info("OrderQueryToolExecutor found order by orderId={}, exists={}", orderId, order != null);
        return Map.of(
                AgentGraphKeys.TOOL_RESULT, toolResult,
                AgentGraphKeys.GROUNDED, order != null);
    }

    private Map<String, Object> queryByUserId(String userId) {
        List<MallOrderDto> orders = mallOrderClient.getOrdersByUserId(userId);
        String toolResult = OrderResultFormatter.formatOrders(orders, userId);
        log.info("OrderQueryToolExecutor found {} order(s) for userId={}", orders.size(), userId);
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
