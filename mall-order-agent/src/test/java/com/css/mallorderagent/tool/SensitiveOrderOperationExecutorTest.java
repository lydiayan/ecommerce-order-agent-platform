package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.tool.client.AfterSalesToolResult;
import com.css.mallorderagent.tool.client.OrderMcpToolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveOrderOperationExecutorTest {

    @Mock
    private com.css.mallorderagent.tool.client.OrderMcpToolClient orderMcpToolClient;

    @InjectMocks
    private SensitiveOrderOperationExecutor executor;

    @Test
    void executeRefundCreatesTicket() {
        when(orderMcpToolClient.submitAfterSalesRequest("ORD20250414005", "USER1005", "退款"))
                .thenReturn(new AfterSalesToolResult(true, """
                        已成功提交退款申请。
                        - 订单号：ORD20250414005
                        - 工单号：SR1234567890
                        后续可在「我的订单」查看进度，客服将在 1 个工作日内处理。
                        """, null, null, List.of(), List.of(), null, null));

        Map<String, Object> data = new HashMap<>();
        data.put(AgentGraphKeys.QUERY, "退款");
        data.put(AgentGraphKeys.USER_ID, "USER1005");
        data.put(AgentGraphKeys.TOOL_RESULT, "[1] ORD20250414005 | 已完成 | 5499");
        OverAllState state = new OverAllState(data);

        SensitiveOperationResult result = executor.execute(state);
        assertTrue(result.success());
        assertTrue(result.message().contains("退款"));
        assertTrue(result.message().contains("ORD20250414005"));
        assertTrue(result.message().contains("工单号"));
    }

    @Test
    void executeReturnPreservesBusinessRejectionReason() {
        when(orderMcpToolClient.submitAfterSalesRequest("ORD20260810003", "USER1002", "退货"))
                .thenReturn(new AfterSalesToolResult(false, "该订单已超过7天退货期限",
                        "BUSINESS_REJECTION", "INELIGIBLE", List.of("RETURN_WINDOW_EXPIRED"),
                        List.of(), "NONE", "refund-v2026.08.18"));

        SensitiveOperationResult result = executor.execute(state("退货", "USER1002", "ORD20260810003"));

        assertFalse(result.success());
        assertTrue(result.grounded());
        assertEquals(SensitiveOperationResult.REJECTED, result.outcome());
        assertEquals("退货申请未提交：该订单已超过7天退货期限", result.message());
    }

    @Test
    void executeReturnKeepsTechnicalFailureSeparate() {
        OrderMcpToolException failure = new OrderMcpToolException("connection refused");
        when(orderMcpToolClient.submitAfterSalesRequest("ORD20260810003", "USER1002", "退货"))
                .thenThrow(failure);

        SensitiveOperationResult result = executor.execute(state("退货", "USER1002", "ORD20260810003"));

        assertFalse(result.success());
        assertFalse(result.grounded());
        assertEquals(SensitiveOperationResult.FAILED, result.outcome());
        assertEquals("退货执行失败：订单 MCP 服务暂时不可用，请稍后重试。", result.message());
        assertEquals(failure, result.error());
    }

    private static OverAllState state(String query, String userId, String orderId) {
        Map<String, Object> data = new HashMap<>();
        data.put(AgentGraphKeys.QUERY, query);
        data.put(AgentGraphKeys.USER_ID, userId);
        data.put(AgentGraphKeys.TOOL_RESULT, "[1] " + orderId + " | 已完成 | 5499");
        return new OverAllState(data);
    }
}
