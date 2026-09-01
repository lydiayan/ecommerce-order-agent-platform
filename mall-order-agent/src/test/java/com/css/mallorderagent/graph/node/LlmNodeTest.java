package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.planner.ActionDefinitions;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.prompt.BuiltPrompt;
import com.css.mallorderagent.stream.AgentStreamSessionRegistry;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.example.mallorderobservability.model.TraceEventType;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmNodeTest {

    @Test
    void emitsLlmSpanWithModelTokensAndOutput() {
        ChatClient chatClient = mock(ChatClient.class);
        AgentStreamSessionRegistry streamRegistry = mock(AgentStreamSessionRegistry.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("qwen-plus")
                .usage(new DefaultUsage(120, 36))
                .build();
        Generation generation = new Generation(
                new AssistantMessage("订单已完成。"),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        when(responseSpec.chatResponse()).thenReturn(new ChatResponse(List.of(generation), metadata));

        RagDocumentProperties properties = new RagDocumentProperties();
        LlmNode node = new LlmNode(chatClient, properties, streamRegistry);
        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "查询订单 ORD20250101120000",
                AgentGraphKeys.PLAN, new PlanResult("ORDER_QUERY", ActionDefinitions.orderQueryPipeline()),
                AgentGraphKeys.BUILT_PROMPT, new BuiltPrompt("system", "user"),
                AgentGraphKeys.CONTEXT_HIT_COUNT, 0));

        List<TraceEvent> events = new ArrayList<>();
        ObservabilityProperties observability = new ObservabilityProperties();
        observability.setEnabled(true);
        observability.setServiceName("test-agent");
        RagTraceService traceService = new RagTraceService(events::add, observability);

        Map<String, Object> result;
        try (RagTraceScope root = traceService.begin("agent.ask")) {
            RagTracingAdvisor.bindParentScope(root);
            try {
                result = node.apply(state);
            } finally {
                RagTracingAdvisor.clearParentScope();
            }
        }

        assertEquals("订单已完成。", result.get(AgentGraphKeys.ANSWER));
        TraceEvent llmEnd = events.stream()
                .filter(event -> event.getEventType() == TraceEventType.SPAN_END)
                .filter(event -> RagTraceService.LLM_OPERATION.equals(event.getOperation()))
                .findFirst()
                .orElseThrow();
        assertEquals("OK", llmEnd.getStatus());
        assertEquals("qwen-plus", llmEnd.getAttributes().get("model"));
        assertEquals(120, llmEnd.getAttributes().get("inputToken"));
        assertEquals(36, llmEnd.getAttributes().get("outputToken"));
        assertEquals("订单已完成。".length(), llmEnd.getAttributes().get("outputLength"));
        assertEquals(false, llmEnd.getAttributes().get("streaming"));
    }

    @Test
    void streamsDeltasAndReturnsTheCompleteAnswer() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec responseSpec = mock(ChatClient.StreamResponseSpec.class);
        AgentStreamSessionRegistry streamRegistry = mock(AgentStreamSessionRegistry.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(responseSpec);

        ChatResponse first = new ChatResponse(List.of(new Generation(new AssistantMessage("订单可以"))));
        ChatResponse second = new ChatResponse(List.of(new Generation(new AssistantMessage("申请退款。"))));
        when(responseSpec.chatResponse()).thenReturn(Flux.just(first, second));
        when(streamRegistry.cancellationSignal("stream-001")).thenReturn(Mono.never());
        when(streamRegistry.isCancelled("stream-001")).thenReturn(false);

        RagDocumentProperties properties = new RagDocumentProperties();
        LlmNode node = new LlmNode(chatClient, properties, streamRegistry);
        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "ORD20260810001 可以退款吗",
                AgentGraphKeys.PLAN, new PlanResult("ORDER_POLICY_QUERY", ActionDefinitions.orderQueryPipeline()),
                AgentGraphKeys.BUILT_PROMPT, new BuiltPrompt("system", "user"),
                AgentGraphKeys.CONTEXT_HIT_COUNT, 1,
                AgentGraphKeys.STREAM_ID, "stream-001"));

        List<TraceEvent> events = new ArrayList<>();
        ObservabilityProperties observability = new ObservabilityProperties();
        observability.setEnabled(true);
        observability.setServiceName("test-agent");
        RagTraceService traceService = new RagTraceService(events::add, observability);

        Map<String, Object> result;
        try (RagTraceScope root = traceService.begin("agent.ask")) {
            RagTracingAdvisor.bindParentScope(root);
            try {
                result = node.apply(state);
            } finally {
                RagTracingAdvisor.clearParentScope();
            }
        }

        assertEquals("订单可以申请退款。", result.get(AgentGraphKeys.ANSWER));
        verify(streamRegistry).emitDelta("stream-001", "订单可以");
        verify(streamRegistry).emitDelta("stream-001", "申请退款。");
        TraceEvent llmEnd = events.stream()
                .filter(event -> event.getEventType() == TraceEventType.SPAN_END)
                .filter(event -> RagTraceService.LLM_OPERATION.equals(event.getOperation()))
                .findFirst()
                .orElseThrow();
        assertEquals(true, llmEnd.getAttributes().get("streaming"));
        assertEquals(2L, llmEnd.getAttributes().get("chunkCount"));
        assertEquals("订单可以申请退款。".length(), llmEnd.getAttributes().get("outputLength"));
        assertTrue(((Number) llmEnd.getAttributes().get("ttftMs")).longValue() >= 0);
        assertEquals(llmEnd.getAttributes().get("ttftMs"),
                llmEnd.getAttributes().get("firstTokenLatencyMs"));
    }
}
