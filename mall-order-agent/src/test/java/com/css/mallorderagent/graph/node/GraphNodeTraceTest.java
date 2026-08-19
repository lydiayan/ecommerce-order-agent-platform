package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.planner.ActionDefinition;
import com.css.mallorderagent.planner.ActionType;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.planner.executor.ActionExecutorRegistry;
import com.css.mallorderagent.tool.SensitiveOrderOperationExecutor;
import com.css.mallorderagent.tool.SensitiveOperationResult;
import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallordermemory.service.UserProfileService;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.example.mallorderobservability.model.TraceEventType;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphNodeTraceTest {

    @Test
    void actionRunnerEmitsExecutionSummary() {
        ActionExecutorRegistry registry = mock(ActionExecutorRegistry.class);
        when(registry.execute(anyString(), any())).thenReturn(Map.of());
        ActionRunnerNode node = new ActionRunnerNode(registry);
        PlanResult plan = new PlanResult("MEMORY_ONLY", List.of(
                new ActionDefinition("MEMORY_LOAD", ActionType.MEMORY, "memoryNode")));
        List<TraceEvent> events = new ArrayList<>();

        traced(events, () -> node.apply(new OverAllState(Map.of(
                AgentGraphKeys.PLAN, plan,
                AgentGraphKeys.SESSION_ID, "conversation-001"))));

        TraceEvent end = spanEnd(events, ActionRunnerNode.NODE_NAME);
        assertEquals("OK", end.getStatus());
        assertEquals("MEMORY_ONLY", end.getAttributes().get("planStrategy"));
        assertEquals(1, end.getAttributes().get("executedActionCount"));
        assertEquals(List.of("MEMORY_LOAD"), end.getAttributes().get("executedActions"));
        assertEquals(false, end.getAttributes().get("shortCircuited"));
    }

    @Test
    void answerEmitsPersistenceOutcomeWithoutAnswerContent() {
        HybridMemoryManager memoryManager = mock(HybridMemoryManager.class);
        when(memoryManager.getDefaultUserId()).thenReturn("default-user");
        AnswerNode node = new AnswerNode(memoryManager);
        List<TraceEvent> events = new ArrayList<>();

        traced(events, () -> node.apply(new OverAllState(Map.of(
                AgentGraphKeys.USER_ID, "user-001",
                AgentGraphKeys.SESSION_ID, "conversation-001",
                AgentGraphKeys.QUERY, "退款规则",
                AgentGraphKeys.ANSWER, "可以在签收后申请退款",
                AgentGraphKeys.GROUNDED, true,
                AgentGraphKeys.PLAN_STRATEGY, "RAG_QA"))));

        verify(memoryManager).addExchange("user-001", "conversation-001", "退款规则", "可以在签收后申请退款");
        TraceEvent end = spanEnd(events, AnswerNode.NODE_NAME);
        assertEquals("OK", end.getStatus());
        assertEquals(10, end.getAttributes().get("answerLength"));
        assertEquals(true, end.getAttributes().get("grounded"));
        assertEquals(true, end.getAttributes().get("memoryPersisted"));
        assertEquals(false, end.getAttributes().containsKey("answer"));
    }

    @Test
    void humanEmitsFeedbackDecisionAndReviewDecision() {
        HumanNode node = new HumanNode();
        List<TraceEvent> events = new ArrayList<>();
        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.SESSION_ID, "conversation-001",
                AgentGraphKeys.QUERY, "退款订单 ORD20250414005",
                AgentGraphKeys.ANSWER, "请确认是否执行退款",
                AgentGraphKeys.PLAN_STRATEGY, "DANGEROUS_ORDER_OP",
                AgentGraphKeys.HUMAN_REVIEW_ENABLED, true,
                AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, true,
                AgentGraphKeys.HUMAN_FEEDBACK, Map.of("approved", true)));

        Map<String, Object> result = traced(events, () -> node.apply(state));

        assertEquals(HumanNode.NEXT_SENSITIVE_OP, result.get(AgentGraphKeys.NEXT_NODE));
        TraceEvent end = spanEnd(events, HumanNode.NODE_NAME);
        assertEquals(true, end.getAttributes().get("approved"));
        assertEquals(HumanNode.NEXT_SENSITIVE_OP, end.getAttributes().get("nextNode"));

        OverAllState reviewState = new OverAllState(Map.of(
                AgentGraphKeys.SESSION_ID, "conversation-002",
                AgentGraphKeys.QUERY, "退款订单 ORD20250414005",
                AgentGraphKeys.ANSWER, "请确认是否执行退款",
                AgentGraphKeys.PLAN_STRATEGY, "DANGEROUS_ORDER_OP",
                AgentGraphKeys.HUMAN_REVIEW_ENABLED, true,
                AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, true,
                AgentGraphKeys.HUMAN_FEEDBACK, Map.of()));
        Optional<?> interruption = traced(events, () -> node.interrupt("human", reviewState, null));

        assertTrue(interruption.isPresent());
        TraceEvent reviewEnd = spanEnd(events, HumanNode.REVIEW_TRACE_OPERATION);
        assertEquals(true, reviewEnd.getAttributes().get("reviewRequired"));
        assertEquals("sensitive_operation", reviewEnd.getAttributes().get("decisionReason"));
    }

    @Test
    void memoryEmitsSourceCountsAndRetrievalStatus() {
        HybridMemoryManager memoryManager = mock(HybridMemoryManager.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(memoryManager.getDefaultUserId()).thenReturn("default-user");
        when(memoryManager.getRecentMessages("user-001", "conversation-001", 20)).thenReturn(List.of(
                new UserMessage("之前的问题"),
                new AssistantMessage("之前的回答")));
        when(embeddingModel.embed("退款规则")).thenReturn(new float[]{0.1f, 0.2f});
        when(memoryManager.searchLongTerm(any(float[].class), anyInt())).thenReturn(List.of());
        when(memoryManager.formatLongTermContext(List.of())).thenReturn("");
        MemoryNode node = new MemoryNode(memoryManager, Optional.<UserProfileService>empty(), embeddingModel);
        List<TraceEvent> events = new ArrayList<>();

        traced(events, () -> node.apply(new OverAllState(Map.of(
                AgentGraphKeys.USER_ID, "user-001",
                AgentGraphKeys.SESSION_ID, "conversation-001",
                AgentGraphKeys.QUERY, "退款规则"))));

        TraceEvent end = spanEnd(events, MemoryNode.NODE_NAME);
        assertEquals("OK", end.getStatus());
        assertEquals(1, end.getAttributes().get("historyCount"));
        assertEquals(0, end.getAttributes().get("longTermMemoryCount"));
        assertEquals(true, end.getAttributes().get("longTermMemoryRetrievalSucceeded"));
        assertEquals(0, end.getAttributes().get("memoryCount"));
    }

    @Test
    void sensitiveOperationEmitsResultSummary() {
        SensitiveOrderOperationExecutor executor = mock(SensitiveOrderOperationExecutor.class);
        when(executor.execute(any())).thenReturn(SensitiveOperationResult.success(
                "退款", "ORD20250414005", "USER1005", "退款申请已提交"));
        SensitiveOperationNode node = new SensitiveOperationNode(executor);
        List<TraceEvent> events = new ArrayList<>();

        traced(events, () -> node.apply(new OverAllState(Map.of(
                AgentGraphKeys.SESSION_ID, "conversation-001",
                AgentGraphKeys.PLAN_STRATEGY, "DANGEROUS_ORDER_OP",
                AgentGraphKeys.USER_ID, "USER1005"))));

        TraceEvent end = spanEnd(events, SensitiveOperationNode.NODE_NAME);
        assertEquals("OK", end.getStatus());
        assertEquals(7, end.getAttributes().get("resultLength"));
        assertEquals(true, end.getAttributes().get("grounded"));
        assertEquals("SUCCEEDED", end.getAttributes().get("executionStatus"));
    }

    @Test
    void sensitiveOperationMarksSpanAsError() {
        SensitiveOrderOperationExecutor executor = mock(SensitiveOrderOperationExecutor.class);
        doThrow(new IllegalStateException("sensitive operation failed")).when(executor).execute(any());
        SensitiveOperationNode node = new SensitiveOperationNode(executor);
        List<TraceEvent> events = new ArrayList<>();

        assertThrows(IllegalStateException.class,
                () -> traced(events, () -> node.apply(new OverAllState(Map.of()))));

        TraceEvent end = spanEnd(events, SensitiveOperationNode.NODE_NAME);
        assertEquals("ERROR", end.getStatus());
        assertEquals("IllegalStateException", end.getErrorMessage());
    }

    private static <T> T traced(List<TraceEvent> events, Supplier<T> action) {
        RagTraceService traceService = traceService(events);
        try (RagTraceScope root = traceService.begin("agent.ask")) {
            RagTracingAdvisor.bindParentScope(root);
            try {
                return action.get();
            } finally {
                RagTracingAdvisor.clearParentScope();
            }
        }
    }

    private static RagTraceService traceService(List<TraceEvent> events) {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setEnabled(true);
        properties.setServiceName("test-agent");
        return new RagTraceService(events::add, properties);
    }

    private static TraceEvent spanEnd(List<TraceEvent> events, String operation) {
        return events.stream()
                .filter(event -> event.getEventType() == TraceEventType.SPAN_END)
                .filter(event -> operation.equals(event.getOperation()))
                .findFirst()
                .orElseThrow();
    }
}
