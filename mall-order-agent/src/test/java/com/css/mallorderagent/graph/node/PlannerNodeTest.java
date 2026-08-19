package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.planner.ActionDefinitions;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.planner.Planner;
import com.css.mallorderagent.prompt.BuiltPrompt;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.example.mallorderobservability.model.TraceEventType;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlannerNodeTest {

    @Test
    void emitsPlannerSpanWithPlanDetails() {
        PlanResult plan = new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline());
        PlannerNode node = new PlannerNode(query -> plan);
        List<TraceEvent> events = new ArrayList<>();
        RagTraceService traceService = traceService(events);

        Map<String, Object> result;
        try (RagTraceScope root = traceService.begin("agent.ask")) {
            RagTracingAdvisor.bindParentScope(root);
            try {
                result = node.apply(new OverAllState(Map.of(
                        AgentGraphKeys.QUERY, "退款规则是什么",
                        AgentGraphKeys.SESSION_ID, "conversation-001")));
            } finally {
                RagTracingAdvisor.clearParentScope();
            }
        }

        assertEquals("RAG_QA", result.get(AgentGraphKeys.PLAN_STRATEGY));
        TraceEvent plannerStart = plannerEvent(events, TraceEventType.SPAN_START);
        TraceEvent plannerEnd = plannerEvent(events, TraceEventType.SPAN_END);
        assertEquals(TracePrivacy.fingerprint("退款规则是什么"),
                plannerStart.getAttributes().get("queryFingerprint"));
        assertFalse(plannerStart.getAttributes().containsKey("query"));
        assertEquals("conversation-001", plannerStart.getAttributes().get("conversationId"));
        assertEquals(plannerStart.getSpanId(), plannerEnd.getSpanId());
        assertEquals("OK", plannerEnd.getStatus());
        assertEquals("RAG_QA", plannerEnd.getAttributes().get("planStrategy"));
        assertEquals(3, plannerEnd.getAttributes().get("actionCount"));
        assertEquals(List.of("MEMORY_LOAD", "KNOWLEDGE_SEARCH", "LLM_GENERATE"),
                plannerEnd.getAttributes().get("actions"));
        assertEquals(false, plannerEnd.getAttributes().get("humanApprovalRequired"));
    }

    @Test
    void marksPlannerSpanAsErrorWhenPlanningFails() {
        Planner failingPlanner = query -> {
            throw new IllegalStateException("planner failed");
        };
        PlannerNode node = new PlannerNode(failingPlanner);
        List<TraceEvent> events = new ArrayList<>();
        RagTraceService traceService = traceService(events);

        try (RagTraceScope root = traceService.begin("agent.ask")) {
            RagTracingAdvisor.bindParentScope(root);
            try {
                assertThrows(IllegalStateException.class,
                        () -> node.apply(new OverAllState(Map.of(AgentGraphKeys.QUERY, "测试问题"))));
            } finally {
                RagTracingAdvisor.clearParentScope();
            }
        }

        TraceEvent plannerEnd = plannerEvent(events, TraceEventType.SPAN_END);
        assertEquals("ERROR", plannerEnd.getStatus());
        assertEquals("IllegalStateException", plannerEnd.getErrorMessage());
    }

    @Test
    void deniesOrderQueryWhenDemoPersonaHasNoOrderCapability() {
        PlannerNode node = new PlannerNode(query ->
                new PlanResult("ORDER_QUERY", ActionDefinitions.orderQueryPipeline()));

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "查询我的订单",
                AgentGraphKeys.CAPABILITIES, List.of("KNOWLEDGE_SEARCH"))));

        assertEquals("CAPABILITY_DENIED", result.get(AgentGraphKeys.PLAN_STRATEGY));
        assertEquals("当前演示身份没有订单查询能力。请切换到销售或客户身份后重试。",
                result.get(AgentGraphKeys.ANSWER));
    }

    @Test
    void deniesOrderPolicyQueryWhenDemoPersonaHasNoOrderCapability() {
        PlannerNode node = new PlannerNode(query ->
                new PlanResult("ORDER_POLICY_QUERY", ActionDefinitions.orderPolicyQueryPipeline()));

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "ORD20260810001 是否可以退款",
                AgentGraphKeys.CAPABILITIES, List.of("KNOWLEDGE_SEARCH"))));

        assertEquals("CAPABILITY_DENIED", result.get(AgentGraphKeys.PLAN_STRATEGY));
        assertEquals("当前演示身份没有订单查询能力。请切换到销售或客户身份后重试。",
                result.get(AgentGraphKeys.ANSWER));
    }

    @Test
    void clearsPreviousTurnOutputsBeforeExecutingNewPlan() {
        PlannerNode node = new PlannerNode(query ->
                new PlanResult("ORDER_POLICY_QUERY", ActionDefinitions.orderPolicyQueryPipeline()));

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                AgentGraphKeys.QUERY, "ORD20260810001 可以退款吗",
                AgentGraphKeys.ANSWER, "上一轮订单详情回答",
                AgentGraphKeys.TOOL_RESULT, "上一轮订单详情工具结果",
                AgentGraphKeys.CONTEXT, "上一轮知识库内容",
                AgentGraphKeys.CONTEXT_HIT_COUNT, 5,
                AgentGraphKeys.GROUNDED, true,
                AgentGraphKeys.BUILT_PROMPT, new BuiltPrompt("旧系统提示", "旧用户提示"),
                AgentGraphKeys.RETRIEVAL, new SearchResponse("旧问题", 1, List.of()),
                AgentGraphKeys.HUMAN_FEEDBACK, Map.of("approved", true),
                AgentGraphKeys.NEXT_NODE, "answer")));

        assertEquals("ORDER_POLICY_QUERY", result.get(AgentGraphKeys.PLAN_STRATEGY));
        assertEquals("", result.get(AgentGraphKeys.ANSWER));
        assertEquals("", result.get(AgentGraphKeys.TOOL_RESULT));
        assertEquals("", result.get(AgentGraphKeys.CONTEXT));
        assertEquals(0, result.get(AgentGraphKeys.CONTEXT_HIT_COUNT));
        assertEquals(false, result.get(AgentGraphKeys.GROUNDED));
        assertEquals(Map.of(), result.get(AgentGraphKeys.HUMAN_FEEDBACK));
        assertEquals("", result.get(AgentGraphKeys.NEXT_NODE));
    }

    private static RagTraceService traceService(List<TraceEvent> events) {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setEnabled(true);
        properties.setServiceName("test-agent");
        return new RagTraceService(events::add, properties);
    }

    private static TraceEvent plannerEvent(List<TraceEvent> events, TraceEventType eventType) {
        return events.stream()
                .filter(event -> event.getEventType() == eventType)
                .filter(event -> PlannerNode.NODE_NAME.equals(event.getOperation()))
                .findFirst()
                .orElseThrow();
    }
}
