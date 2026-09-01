package com.css.mallorderagent.tool.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class OrderMcpToolClientTest {

    @Mock
    private ToolCallbackProvider toolCallbackProvider;

    @Mock
    private ToolCallback cancelOrderCallback;

    @Test
    void findToolMatchesPrefixedName() {
        ToolDefinition definition = ToolDefinition.builder()
                .name("mall_order_agent_mcp_order_server_cancelOrder")
                .description("cancel")
                .inputSchema("{}")
                .build();
        when(cancelOrderCallback.getToolDefinition()).thenReturn(definition);
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{cancelOrderCallback});

        OrderMcpToolClient client = new OrderMcpToolClient(toolCallbackProvider, new com.fasterxml.jackson.databind.ObjectMapper());
        Optional<ToolCallback> found = client.findTool("cancelOrder");

        assertTrue(found.isPresent());
        assertEquals(cancelOrderCallback, found.get());
    }

    @Test
    void cancelOrderParsesBooleanResult() throws Exception {
        ToolDefinition definition = ToolDefinition.builder()
                .name("cancelOrder")
                .description("cancel")
                .inputSchema("{}")
                .build();
        when(cancelOrderCallback.getToolDefinition()).thenReturn(definition);
        when(cancelOrderCallback.call(anyString())).thenReturn("true");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{cancelOrderCallback});

        OrderMcpToolClient client = new OrderMcpToolClient(toolCallbackProvider, new com.fasterxml.jackson.databind.ObjectMapper());
        assertTrue(client.cancelOrder("ORD001", "USER001"));
    }

    @Test
    void afterSalesParsesStructuredBusinessRejection() throws Exception {
        ToolDefinition definition = ToolDefinition.builder()
                .name("submitAfterSalesRequest")
                .description("after sales")
                .inputSchema("{}")
                .build();
        when(cancelOrderCallback.getToolDefinition()).thenReturn(definition);
        when(cancelOrderCallback.call(anyString())).thenReturn("""
                {"success":false,"message":"该订单已超过7天退货期限",\
                 "failureType":"BUSINESS_REJECTION","decision":"INELIGIBLE",\
                 "reasonCodes":["RETURN_WINDOW_EXPIRED"],"missingFields":[],\
                 "nextAction":"NONE","policyVersion":"refund-v2026.08.18"}
                """);
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{cancelOrderCallback});
        OrderMcpToolClient client = new OrderMcpToolClient(
                toolCallbackProvider, new com.fasterxml.jackson.databind.ObjectMapper());

        AfterSalesToolResult result = client.submitAfterSalesRequest("ORD001", "USER001", "退货");

        assertFalse(result.success());
        assertEquals("BUSINESS_REJECTION", result.failureType());
        assertEquals(List.of("RETURN_WINDOW_EXPIRED"), result.reasonCodes());
        assertEquals("该订单已超过7天退货期限", result.message());
    }

    @Test
    void afterSalesUnwrapsSpringAiMcpContentArray() throws Exception {
        ToolDefinition definition = ToolDefinition.builder()
                .name("submitAfterSalesRequest")
                .description("after sales")
                .inputSchema("{}")
                .build();
        when(cancelOrderCallback.getToolDefinition()).thenReturn(definition);
        when(cancelOrderCallback.call(anyString())).thenReturn("""
                [{"type":"text","text":"{\\"success\\":false,\\"message\\":\\"该订单已超过7天退货期限\\",\\"failureType\\":\\"BUSINESS_REJECTION\\",\\"decision\\":\\"INELIGIBLE\\",\\"reasonCodes\\":[\\"RETURN_WINDOW_EXPIRED\\"],\\"missingFields\\":[],\\"nextAction\\":\\"NONE\\",\\"policyVersion\\":\\"refund-v2026.08.18\\"}"}]
                """);
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{cancelOrderCallback});
        OrderMcpToolClient client = new OrderMcpToolClient(
                toolCallbackProvider, new com.fasterxml.jackson.databind.ObjectMapper());

        AfterSalesToolResult result = client.submitAfterSalesRequest("ORD001", "USER001", "退货");

        assertFalse(result.success());
        assertEquals("BUSINESS_REJECTION", result.failureType());
        assertEquals("该订单已超过7天退货期限", result.message());
    }

    @Test
    void afterSalesKeepsCompatibilityWithLegacyTextResult() throws Exception {
        ToolDefinition definition = ToolDefinition.builder()
                .name("submitAfterSalesRequest")
                .description("after sales")
                .inputSchema("{}")
                .build();
        when(cancelOrderCallback.getToolDefinition()).thenReturn(definition);
        when(cancelOrderCallback.call(anyString())).thenReturn("已成功提交退货申请");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{cancelOrderCallback});
        OrderMcpToolClient client = new OrderMcpToolClient(
                toolCallbackProvider, new com.fasterxml.jackson.databind.ObjectMapper());

        AfterSalesToolResult result = client.submitAfterSalesRequest("ORD001", "USER001", "退货");

        assertTrue(result.success());
        assertEquals("已成功提交退货申请", result.message());
    }

    @Test
    void malformedStructuredAfterSalesResultIsNotTreatedAsSuccess() throws Exception {
        ToolDefinition definition = ToolDefinition.builder()
                .name("submitAfterSalesRequest")
                .description("after sales")
                .inputSchema("{}")
                .build();
        when(cancelOrderCallback.getToolDefinition()).thenReturn(definition);
        when(cancelOrderCallback.call(anyString())).thenReturn(
                "{\"success\":false,\"reasonCodes\":7}");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{cancelOrderCallback});
        OrderMcpToolClient client = new OrderMcpToolClient(
                toolCallbackProvider, new com.fasterxml.jackson.databind.ObjectMapper());

        assertThrows(OrderMcpToolException.class,
                () -> client.submitAfterSalesRequest("ORD001", "USER001", "退货"));
    }

    @Test
    void staleMcpSessionDuringToolLookupIsWrappedAsToolFailure() {
        when(toolCallbackProvider.getToolCallbacks())
                .thenThrow(WebClientResponseException.create(404, "Not Found", null, null, null));
        OrderMcpToolClient client = new OrderMcpToolClient(
                toolCallbackProvider, new com.fasterxml.jackson.databind.ObjectMapper());

        OrderMcpToolException failure = assertThrows(OrderMcpToolException.class,
                () -> client.submitAfterSalesRequest("ORD001", "USER001", "退货"));

        assertTrue(failure.getMessage().contains("MCP Tool 调用失败"));
    }
}
