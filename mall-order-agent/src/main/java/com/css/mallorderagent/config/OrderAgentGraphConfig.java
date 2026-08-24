package com.css.mallorderagent.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.node.*;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.planner.PlanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 订单 Agent Graph 编排：Planner → ActionRunner → Prompt → LLM → Human → Answer。
 */
@Configuration
public class OrderAgentGraphConfig {

    private static final Logger log = LoggerFactory.getLogger(OrderAgentGraphConfig.class);

    private static final List<String> STATE_KEYS = List.of(
            AgentGraphKeys.ASK_REQUEST,
            AgentGraphKeys.USER_ID,
            AgentGraphKeys.SESSION_ID,
            AgentGraphKeys.QUERY,
            AgentGraphKeys.PERSONA_CONTEXT,
            AgentGraphKeys.CAPABILITIES,
            AgentGraphKeys.AUTHORIZED_CUSTOMER_IDS,
            AgentGraphKeys.RAG_ROLE_SCOPES,
            AgentGraphKeys.RAG_DEPARTMENT_SCOPES,
            AgentGraphKeys.STREAM_ID,
            AgentGraphKeys.HISTORY,
            AgentGraphKeys.HISTORY_COUNT,
            AgentGraphKeys.USER_PROFILE_CONTEXT,
            AgentGraphKeys.LONG_TERM_MEMORY,
            AgentGraphKeys.MEMORY_COUNT,
            AgentGraphKeys.RETRIEVAL,
            AgentGraphKeys.CONTEXT,
            AgentGraphKeys.CONTEXT_HIT_COUNT,
            AgentGraphKeys.GROUNDED,
            AgentGraphKeys.PLAN,
            AgentGraphKeys.PLAN_STRATEGY,
            AgentGraphKeys.BUILT_PROMPT,
            AgentGraphKeys.TOOL_RESULT,
            AgentGraphKeys.ANSWER,
            AgentGraphKeys.HUMAN_FEEDBACK,
            AgentGraphKeys.NEXT_NODE,
            AgentGraphKeys.HUMAN_REVIEW_ENABLED,
            AgentGraphKeys.HUMAN_APPROVAL_REQUIRED,
            AgentGraphKeys.APPROVAL_REASON
    );

    private static final Map<String, String> ACTION_RUNNER_ROUTES = Map.of(
            "prompt", PromptNode.NODE_NAME,
            "answer", AnswerNode.NODE_NAME
    );

    private static final Map<String, String> LLM_ROUTES = Map.of(
            "human", HumanNode.NODE_NAME,
            "answer", AnswerNode.NODE_NAME
    );

    private static final Map<String, String> HUMAN_ROUTES = Map.of(
            AnswerNode.NODE_NAME, AnswerNode.NODE_NAME,
            SensitiveOperationNode.NODE_NAME, SensitiveOperationNode.NODE_NAME,
            PlannerNode.NODE_NAME, PlannerNode.NODE_NAME,
            END, END
    );

    @Bean
    public StateGraph orderAgentGraph(PlannerNode plannerNode,
                                      ActionRunnerNode actionRunnerNode,
                                      PromptNode promptNode,
                                      LlmNode llmNode,
                                      HumanNode humanNode,
                                      SensitiveOperationNode sensitiveOperationNode,
                                      AnswerNode answerNode) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            for (String key : STATE_KEYS) {
                strategies.put(key, new ReplaceStrategy());
            }
            return strategies;
        };

        StateGraph graph = new StateGraph(keyStrategyFactory)
                .addNode(PlannerNode.NODE_NAME, node_async(plannerNode))
                .addNode(ActionRunnerNode.NODE_NAME, node_async(actionRunnerNode))
                .addNode(PromptNode.NODE_NAME, node_async(promptNode))
                .addNode(LlmNode.NODE_NAME, node_async(llmNode))
                .addNode(HumanNode.NODE_NAME, interruptableNode(humanNode))
                .addNode(SensitiveOperationNode.NODE_NAME, node_async(sensitiveOperationNode))
                .addNode(AnswerNode.NODE_NAME, node_async(answerNode))
                .addEdge(START, PlannerNode.NODE_NAME)
                .addEdge(PlannerNode.NODE_NAME, ActionRunnerNode.NODE_NAME)
                .addConditionalEdges(ActionRunnerNode.NODE_NAME, edge_async(this::routeAfterActionRunner), ACTION_RUNNER_ROUTES)
                .addEdge(PromptNode.NODE_NAME, LlmNode.NODE_NAME)
                .addConditionalEdges(LlmNode.NODE_NAME, edge_async(this::routeAfterLlm), LLM_ROUTES)
                .addConditionalEdges(HumanNode.NODE_NAME, edge_async(this::routeAfterHuman), HUMAN_ROUTES)
                .addEdge(SensitiveOperationNode.NODE_NAME, AnswerNode.NODE_NAME)
                .addEdge(AnswerNode.NODE_NAME, END);

        GraphRepresentation representation = graph.getGraph(GraphRepresentation.Type.PLANTUML, "order-agent-flow");
        log.info("Order agent graph:\n{}", representation.content());
        return graph;
    }

    @Bean
    public com.alibaba.cloud.ai.graph.CompiledGraph orderAgentCompiledGraph(StateGraph orderAgentGraph,
                                                                            OrderAgentProperties properties)
            throws GraphStateException {
        CompileConfig.Builder builder = CompileConfig.builder();
        if (properties.getGraph().isHumanReviewEnabled()) {
            // 仅当 routeAfterLlm 路由到 human 节点时才会触发中断（普通问答走 answer 直连）
            builder.interruptBefore(HumanNode.NODE_NAME);
            builder.saverConfig(SaverConfig.builder()
                    .register(MemorySaver.builder().build())
                    .build());
            log.info("Order agent graph selective human review enabled, interruptBefore={}",
                    HumanNode.NODE_NAME);
        }
        return orderAgentGraph.compile(builder.build());
    }

    private String routeAfterActionRunner(OverAllState state) {
        if (isDangerousOrderOp(state)) {
            return "prompt";
        }
        String answer = state.value(AgentGraphKeys.ANSWER, "");
        if (!answer.isBlank()) {
            return "answer";
        }
        boolean needLlm = state.value(AgentGraphKeys.PLAN, PlanResult.class)
                .map(PlanResult::needLlm)
                .orElse(false);
        return needLlm ? "prompt" : "answer";
    }

    private static boolean isDangerousOrderOp(OverAllState state) {
        return state.value(AgentGraphKeys.PLAN, PlanResult.class)
                .map(plan -> HumanApprovalDetector.isDangerousOrderOp(plan.strategy()))
                .orElse(HumanApprovalDetector.isDangerousOrderOp(
                        state.value(AgentGraphKeys.PLAN_STRATEGY, "")));
    }

    private String routeAfterLlm(OverAllState state) {
        if (needsHumanReview(state)) {
            return "human";
        }
        return "answer";
    }

    private String routeAfterHuman(OverAllState state) {
        return state.value(AgentGraphKeys.NEXT_NODE, AnswerNode.NODE_NAME);
    }

    static boolean needsHumanReview(OverAllState state) {
        if (!state.value(AgentGraphKeys.HUMAN_REVIEW_ENABLED, false)) {
            return false;
        }
        return state.value(AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, false)
                || isDangerousOrderOp(state);
    }

    private static AsyncNodeActionWithConfig interruptableNode(InterruptableAction interruptable) {
        if (!(interruptable instanceof NodeAction nodeAction)) {
            throw new IllegalArgumentException("Interruptable node must also implement NodeAction");
        }
        return new AsyncNodeActionWithConfig.InterruptableAsyncNodeActionWrapper(
                node_async(nodeAction), interruptable);
    }
}
