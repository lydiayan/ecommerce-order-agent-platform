package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryToolExecutorTest {

    @Mock
    private MallOrderClient mallOrderClient;

    @InjectMocks
    private OrderQueryToolExecutor executor;

    @Test
    void queryByOrderIdFromNaturalLanguage() {
        MallOrderDto order = new MallOrderDto();
        order.setOrderId("ORD20250101120000");
        order.setUserId("USER1005");
        order.setOrderStatus(2);
        order.setTotalAmount(new BigDecimal("99.00"));
        when(mallOrderClient.getOrderById("ORD20250101120000")).thenReturn(order);

        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "帮我查一下订单 ORD20250101120000 的状态",
                AgentGraphKeys.USER_ID, "default_user"));

        Map<String, Object> result = executor.execute(state);

        assertTrue((Boolean) result.get(AgentGraphKeys.GROUNDED));
        assertTrue(result.get(AgentGraphKeys.TOOL_RESULT).toString().contains("ORD20250101120000"));
        assertTrue(result.get(AgentGraphKeys.TOOL_RESULT).toString().contains("已发货"));
    }

    @Test
    void queryByUserIdFromState() {
        when(mallOrderClient.getOrdersByUserId("USER1005")).thenReturn(List.of());

        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "查询我的订单",
                AgentGraphKeys.USER_ID, "USER1005"));

        Map<String, Object> result = executor.execute(state);

        assertEquals(false, result.get(AgentGraphKeys.GROUNDED));
        assertTrue(result.get(AgentGraphKeys.TOOL_RESULT).toString().contains("USER1005"));
    }
}
