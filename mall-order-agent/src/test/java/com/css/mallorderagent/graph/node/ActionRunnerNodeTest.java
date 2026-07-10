package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.ActionDefinitions;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.planner.executor.ActionExecutorRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionRunnerNodeTest {

    @Test
    void executesNonLlmActionsInOrder() {
        ActionExecutorRegistry registry = mock(ActionExecutorRegistry.class);
        when(registry.execute(eq("memoryNode"), any())).thenReturn(Map.of(AgentGraphKeys.HISTORY_COUNT, 1));
        when(registry.execute(eq("retrieveNode"), any())).thenReturn(Map.of(AgentGraphKeys.GROUNDED, true));

        ActionRunnerNode runner = new ActionRunnerNode(registry);
        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.PLAN, new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline()),
                AgentGraphKeys.QUERY, "退款规则"));

        Map<String, Object> result = runner.apply(state);

        verify(registry, times(1)).execute(eq("memoryNode"), any());
        verify(registry, times(1)).execute(eq("retrieveNode"), any());
        assertEquals(true, result.get(AgentGraphKeys.GROUNDED));
        assertEquals(1, result.get(AgentGraphKeys.HISTORY_COUNT));
    }

    @Test
    void stopsWhenRagMissSetsAnswer() {
        ActionExecutorRegistry registry = mock(ActionExecutorRegistry.class);
        when(registry.execute(eq("memoryNode"), any())).thenReturn(Map.of());
        when(registry.execute(eq("retrieveNode"), any())).thenReturn(new HashMap<>(Map.of(
                AgentGraphKeys.GROUNDED, false,
                AgentGraphKeys.ANSWER, AgentGraphSupport.NO_CONTEXT_ANSWER)));

        ActionRunnerNode runner = new ActionRunnerNode(registry);
        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.PLAN, new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline())));

        Map<String, Object> result = runner.apply(state);

        verify(registry, times(1)).execute(eq("retrieveNode"), any());
        assertEquals(AgentGraphSupport.NO_CONTEXT_ANSWER, result.get(AgentGraphKeys.ANSWER));
    }

    @Test
    void skipsLlmAction() {
        ActionExecutorRegistry registry = mock(ActionExecutorRegistry.class);
        when(registry.execute(eq("memoryNode"), any())).thenReturn(Map.of());

        ActionRunnerNode runner = new ActionRunnerNode(registry);
        OverAllState state = new OverAllState(Map.of(
                AgentGraphKeys.PLAN, new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline())));

        runner.apply(state);

        verify(registry, times(0)).execute(eq("llmNode"), any());
    }
}
