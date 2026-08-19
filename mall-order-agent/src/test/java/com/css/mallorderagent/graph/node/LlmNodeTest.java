package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.planner.ActionDefinitions;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.prompt.BuiltPrompt;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmNodeTest {

    @Test
    void emitsLlmSpanWithModelTokensAndOutput() {
        ChatClient chatClient = mock(ChatClient.class);
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
        LlmNode node = new LlmNode(chatClient, properties);
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
    }
}
