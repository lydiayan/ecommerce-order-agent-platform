package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.example.mallorderobservability.model.TraceEventType;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
        when(mallOrderClient.getOrderById("ORD20250101120000", "USER1005")).thenReturn(order);

        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "帮我查一下订单 ORD20250101120000 的状态",
                AgentGraphKeys.USER_ID, "USER1005"));

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

    @Test
    void emitsOrderQueryToolSpanWithExecutionEvidence() {
        MallOrderDto order = new MallOrderDto();
        order.setOrderId("ORD20250101120000");
        when(mallOrderClient.getOrderById("ORD20250101120000", "USER1005")).thenReturn(order);

        List<TraceEvent> events = new ArrayList<>();
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setEnabled(true);
        properties.setServiceName("test-agent");
        RagTraceService traceService = new RagTraceService(events::add, properties);

        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "查询订单 ORD20250101120000",
                AgentGraphKeys.USER_ID, "USER1005"));

        try (RagTraceScope root = traceService.begin("agent.ask")) {
            RagTracingAdvisor.bindParentScope(root);
            try {
                executor.execute(state);
            } finally {
                RagTracingAdvisor.clearParentScope();
            }
        }

        TraceEvent toolEnd = events.stream()
                .filter(event -> event.getEventType() == TraceEventType.SPAN_END)
                .filter(event -> OrderQueryToolExecutor.TRACE_OPERATION.equals(event.getOperation()))
                .findFirst()
                .orElseThrow();
        assertEquals("OK", toolEnd.getStatus());
        assertEquals("ORDER_QUERY", toolEnd.getAttributes().get("toolName"));
        assertEquals(TracePrivacy.fingerprint("ORD20250101120000"),
                toolEnd.getAttributes().get("orderFingerprint"));
        assertEquals(TracePrivacy.fingerprint("USER1005"),
                toolEnd.getAttributes().get("userFingerprint"));
        assertFalse(toolEnd.getAttributes().containsKey("orderId"));
        assertFalse(toolEnd.getAttributes().containsKey("userId"));
        assertEquals(true, toolEnd.getAttributes().get("success"));
        assertEquals(1, toolEnd.getAttributes().get("resultCount"));
    }

    @Test
    void salesCanOnlyQueryAssignedCustomerAndSeesMaskedContactData() {
        MallOrderDto order = new MallOrderDto();
        order.setOrderId("ORD20260810001");
        order.setUserId("USER1001");
        order.setContactPhone("13800000001");
        order.setShippingAddress("DEMO_ADDRESS_001");
        when(mallOrderClient.getOrderById("ORD20260810001", "USER1001")).thenReturn(order);

        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "查询订单 ORD20260810001 的详情",
                AgentGraphKeys.USER_ID, "SALES001",
                AgentGraphKeys.CAPABILITIES, List.of("KNOWLEDGE_SEARCH", "ASSIGNED_ORDER_READ"),
                AgentGraphKeys.AUTHORIZED_CUSTOMER_IDS, List.of("USER1001")));

        Map<String, Object> result = executor.execute(state);

        String text = result.get(AgentGraphKeys.TOOL_RESULT).toString();
        assertTrue((Boolean) result.get(AgentGraphKeys.GROUNDED));
        assertTrue(text.contains("138****0001"));
        assertFalse(text.contains("13800000001"));
        assertTrue(text.contains("DEMO***"));
        verify(mallOrderClient).getOrderById("ORD20260810001", "USER1001");
    }

    @Test
    void salesCannotQueryUnassignedCustomerMentionedInPrompt() {
        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "查询 USER1002 的订单",
                AgentGraphKeys.USER_ID, "SALES001",
                AgentGraphKeys.CAPABILITIES, List.of("KNOWLEDGE_SEARCH", "ASSIGNED_ORDER_READ"),
                AgentGraphKeys.AUTHORIZED_CUSTOMER_IDS, List.of("USER1001")));

        Map<String, Object> result = executor.execute(state);

        assertFalse((Boolean) result.get(AgentGraphKeys.GROUNDED));
        assertTrue(result.get(AgentGraphKeys.TOOL_RESULT).toString().contains("已授权"));
        verify(mallOrderClient, never()).getOrdersByUserId("USER1002");
    }
}
