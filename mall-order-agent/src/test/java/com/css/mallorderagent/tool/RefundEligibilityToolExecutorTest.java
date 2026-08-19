package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.tool.client.OrderMcpToolClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundEligibilityToolExecutorTest {

    @Mock
    private OrderMcpToolClient orderMcpToolClient;

    @InjectMocks
    private RefundEligibilityToolExecutor executor;

    @Test
    void genericRefundQuestionUsesNoReasonAndReturnsAuthoritativeResult() {
        when(orderMcpToolClient.evaluateRefundEligibility(
                "ORD20260810001", "USER1001", "NO_REASON",
                null, null, null, null, List.of()))
                .thenReturn("【退款资格权威结论】\n资格结论：ELIGIBLE");

        Map<String, Object> result = executor.execute(new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "ORD20260810001 是否可以退款",
                AgentGraphKeys.USER_ID, "USER1001")));

        assertEquals(true, result.get(AgentGraphKeys.GROUNDED));
        assertTrue(result.get(AgentGraphKeys.TOOL_RESULT).toString().contains("ELIGIBLE"));
        verify(orderMcpToolClient).evaluateRefundEligibility(
                "ORD20260810001", "USER1001", "NO_REASON",
                null, null, null, null, List.of());
    }

    @Test
    void missingOrderIdDoesNotInvokeMcp() {
        Map<String, Object> result = executor.execute(new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "这个订单可以退款吗",
                AgentGraphKeys.USER_ID, "USER1001")));

        assertEquals(false, result.get(AgentGraphKeys.GROUNDED));
        assertTrue(result.get(AgentGraphKeys.TOOL_RESULT).toString().contains("订单号"));
    }
}
