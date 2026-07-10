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
    private com.css.mallorderagent.tool.client.MallOrderClient mallOrderClient;

    @InjectMocks
    private SensitiveOrderOperationExecutor executor;

    @Test
    void executeRefundCreatesTicket() {
        Map<String, Object> data = new HashMap<>();
        data.put(AgentGraphKeys.QUERY, "退款");
        data.put(AgentGraphKeys.TOOL_RESULT, "[1] ORD20250414005 | 已完成 | 5499");
        OverAllState state = new OverAllState(data);

        String result = executor.execute(state);
        assertTrue(result.contains("退款"));
        assertTrue(result.contains("ORD20250414005"));
        assertTrue(result.contains("工单号"));
    }
}
