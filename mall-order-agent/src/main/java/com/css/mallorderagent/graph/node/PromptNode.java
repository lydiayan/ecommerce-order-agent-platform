package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.memory.ConversationTurn;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.prompt.BuiltPrompt;
import com.css.mallorderagent.prompt.PromptBuilder;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.tracing.PromptBuildSpanAttributes;
import com.example.mallordermilvusrag.tracing.RagTraceOperations;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 节点：读取 Planner 结果，组装 LLM 输入。
 */
@Component
public class PromptNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(PromptNode.class);

    public static final String NODE_NAME = "prompt";

    private final PromptBuilder promptBuilder;
    private final RagDocumentProperties.AskProperties askProperties;

    public PromptNode(PromptBuilder promptBuilder,
                      RagDocumentProperties ragDocumentProperties) {
        this.promptBuilder = promptBuilder;
        this.askProperties = ragDocumentProperties.getAsk();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        List<ConversationTurn> history = AgentGraphSupport.readHistory(state);
        String userProfileContext = state.value(AgentGraphKeys.USER_PROFILE_CONTEXT, "");
        String personaContext = state.value(AgentGraphKeys.PERSONA_CONTEXT, "");
        if (!personaContext.isBlank()) {
            userProfileContext = personaContext + (userProfileContext.isBlank() ? "" : "\n\n" + userProfileContext);
        }
        String longTermMemory = state.value(AgentGraphKeys.LONG_TERM_MEMORY, "");
        String context = state.value(AgentGraphKeys.CONTEXT, "");
        String toolResult = state.value(AgentGraphKeys.TOOL_RESULT, "");

        PlanResult plan = state.value(AgentGraphKeys.PLAN, PlanResult.class)
                .orElseThrow(() -> new IllegalStateException("plan is required before PromptNode"));
        BuiltPrompt built = promptBuilder.build(
                plan,
                history,
                userProfileContext,
                longTermMemory,
                context,
                toolResult,
                query,
                askProperties.getSystemPrompt());

        int contextHitCount = state.value(AgentGraphKeys.CONTEXT_HIT_COUNT, 0);
        int historyCount = state.value(AgentGraphKeys.HISTORY_COUNT, history.size());
        int memoryCount = state.value(AgentGraphKeys.MEMORY_COUNT, 0);

        log.info("PromptNode completed, strategy={}, history={}, contextChunks={}, memoryCount={}",
                plan.strategy(), history.size(), contextHitCount, memoryCount);

        RagTraceScope trace = RagTracingAdvisor.parentScope();
        try (RagTraceScope promptSpan = trace.child(RagTraceOperations.PROMPT_BUILD,
                PromptBuildSpanAttributes.build(
                        askProperties.getPromptVersion(),
                        built.systemPrompt(),
                        built.userMessage(),
                        contextHitCount,
                        historyCount,
                        memoryCount))) {
            promptSpan.attribute("planStrategy", plan.strategy());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(AgentGraphKeys.BUILT_PROMPT, built);
        return updates;
    }
}
