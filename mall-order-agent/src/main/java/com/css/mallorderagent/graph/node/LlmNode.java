package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.prompt.BuiltPrompt;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 节点：调用 ChatClient 生成回答。
 * <p>
 * {@code DANGEROUS_ORDER_OP} 策略下使用模板确认话术，不调用 LLM，避免复读订单列表。
 * </p>
 */
@Component
public class LlmNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(LlmNode.class);

    public static final String NODE_NAME = "llm";

    private final ChatClient chatClient;
    private final RagDocumentProperties.AskProperties askProperties;
    private final ObjectProvider<RagTracingAdvisor> ragTracingAdvisorProvider;

    public LlmNode(ChatClient chatClient,
                   RagDocumentProperties ragDocumentProperties,
                   ObjectProvider<RagTracingAdvisor> ragTracingAdvisorProvider) {
        this.chatClient = chatClient;
        this.askProperties = ragDocumentProperties.getAsk();
        this.ragTracingAdvisorProvider = ragTracingAdvisorProvider;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        PlanResult plan = state.value(AgentGraphKeys.PLAN, PlanResult.class).orElse(null);
        String planStrategy = plan != null ? plan.strategy()
                : state.value(AgentGraphKeys.PLAN_STRATEGY, "");
        boolean dangerousOrderOp = HumanApprovalDetector.isDangerousOrderOp(planStrategy);

        String answer;
        if (dangerousOrderOp) {
            String toolResult = state.value(AgentGraphKeys.TOOL_RESULT, "");
            String context = state.value(AgentGraphKeys.CONTEXT, "");
            answer = HumanApprovalDetector.buildDangerousOrderConfirmation(query, toolResult, context);
            log.info("LlmNode skipped LLM for DANGEROUS_ORDER_OP, query='{}', orderIdPresent={}",
                    query, HumanApprovalDetector.extractFirstOrderId(toolResult).isPresent());
        } else {
            BuiltPrompt built = state.value(AgentGraphKeys.BUILT_PROMPT, BuiltPrompt.class)
                    .orElseThrow(() -> new IllegalStateException("builtPrompt is required before LlmNode"));
            int contextChunks = state.value(AgentGraphKeys.CONTEXT_HIT_COUNT, 0);
            RagTraceScope trace = RagTracingAdvisor.parentScope();
            answer = callLlm(trace, query, built, contextChunks);
            log.info("LlmNode completed, strategy={}, answerLength={}", planStrategy, answer.length());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(AgentGraphKeys.ANSWER, answer);
        boolean needsReview = dangerousOrderOp
                || state.value(AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, false)
                || HumanApprovalDetector.queryRequiresApproval(query)
                || HumanApprovalDetector.answerRequiresApproval(answer);
        if (needsReview) {
            updates.put(AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, true);
            updates.put(AgentGraphKeys.APPROVAL_REASON,
                    HumanApprovalDetector.resolveReason(query, answer));
        }
        return updates;
    }

    private String callLlm(RagTraceScope trace, String query, BuiltPrompt built, int contextChunks) {
        RagTracingAdvisor ragTracingAdvisor = ragTracingAdvisorProvider.getIfAvailable();
        RagTracingAdvisor.tag("userQuery", query);
        RagTracingAdvisor.tag("contextChunks", contextChunks);
        RagTracingAdvisor.bindParentScope(trace);
        try {
            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(askProperties.getModel())
                            .temperature(askProperties.getTemperature())
                            .build())
                    .system(built.systemPrompt())
                    .user(built.userMessage());
            if (ragTracingAdvisor != null) {
                requestSpec.advisors(ragTracingAdvisor);
            }
            return requestSpec.call().content();
        } finally {
            RagTracingAdvisor.clearParentScope();
            RagTracingAdvisor.clearTags();
        }
    }
}
