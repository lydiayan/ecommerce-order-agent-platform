package com.css.mallorderagent.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.node.ActionRunnerNode;
import com.css.mallorderagent.graph.node.AnswerNode;
import com.css.mallorderagent.graph.node.HumanNode;
import com.css.mallorderagent.graph.node.LlmNode;
import com.css.mallorderagent.graph.node.PlannerNode;
import com.css.mallorderagent.graph.node.PromptNode;
import com.css.mallorderagent.graph.node.SensitiveOperationNode;
import com.css.mallorderagent.planner.ActionDefinition;
import com.css.mallorderagent.planner.ActionType;
import com.css.mallorderagent.planner.PlanResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderAgentGraphConfigTest {

    @Test
    void generatesANewAnswerForEachTurnInTheSameCheckpointThread() throws Exception {
        PlannerNode planner = new PlannerNode(query -> new PlanResult(
                query.contains("退款") ? "ORDER_POLICY_QUERY" : "ORDER_QUERY",
                List.of(new ActionDefinition("GENERATE", ActionType.LLM, "llm"))));
        ActionRunnerNode actionRunner = mock(ActionRunnerNode.class);
        PromptNode prompt = mock(PromptNode.class);
        LlmNode llm = mock(LlmNode.class);
        HumanNode human = mock(HumanNode.class);
        SensitiveOperationNode sensitiveOperation = mock(SensitiveOperationNode.class);
        AnswerNode answer = mock(AnswerNode.class);

        when(actionRunner.apply(any())).thenReturn(Map.of());
        when(prompt.apply(any())).thenReturn(Map.of());
        when(llm.apply(any())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0, com.alibaba.cloud.ai.graph.OverAllState.class)
                    .value(AgentGraphKeys.QUERY, "");
            return Map.of(AgentGraphKeys.ANSWER, "本轮回答：" + query);
        });
        when(human.apply(any())).thenReturn(Map.of());
        when(sensitiveOperation.apply(any())).thenReturn(Map.of());
        when(answer.apply(any())).thenReturn(Map.of());

        OrderAgentGraphConfig graphConfig = new OrderAgentGraphConfig();
        StateGraph graph = graphConfig.orderAgentGraph(
                planner, actionRunner, prompt, llm, human, sensitiveOperation, answer);
        OrderAgentProperties properties = new OrderAgentProperties();
        properties.getGraph().setHumanReviewEnabled(true);
        CompiledGraph compiledGraph = graphConfig.orderAgentCompiledGraph(graph, properties);
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId("same-conversation").build();

        NodeOutput first = compiledGraph.invokeAndGetOutput(
                        Map.of(AgentGraphKeys.QUERY, "查询订单 ORD20260810001 的详情"), runnableConfig)
                .orElseThrow();
        NodeOutput second = compiledGraph.invokeAndGetOutput(
                        Map.of(AgentGraphKeys.QUERY, "ORD20260810001 可以退款吗"), runnableConfig)
                .orElseThrow();

        assertEquals("本轮回答：查询订单 ORD20260810001 的详情",
                first.state().value(AgentGraphKeys.ANSWER, ""));
        assertEquals("本轮回答：ORD20260810001 可以退款吗",
                second.state().value(AgentGraphKeys.ANSWER, ""));
        assertEquals("ORDER_POLICY_QUERY",
                second.state().value(AgentGraphKeys.PLAN_STRATEGY, ""));
    }
}
