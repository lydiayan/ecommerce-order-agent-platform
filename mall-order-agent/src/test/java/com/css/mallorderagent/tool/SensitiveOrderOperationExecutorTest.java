package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
                .thenReturn("""
                        已成功提交退款申请。
                        - 订单号：ORD20250414005
                        - 工单号：SR1234567890
                        后续可在「我的订单」查看进度，客服将在 1 个工作日内处理。
                        """);

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
}
