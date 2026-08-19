package com.css.mallorderagent.tool.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
