package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 人类反馈节点：在 LLM 生成答案后等待人工审核，并决定后续路由。
 * <p>
 * 配合 {@link InterruptableAction#interrupt} 在敏感操作时暂停 Graph，等待人工反馈。
 * </p>
 */
@Component
public class HumanNode implements NodeAction, InterruptableAction {

    private static final Logger log = LoggerFactory.getLogger(HumanNode.class);

    public static final String NODE_NAME = "human";

    /** 人工审核通过后路由到的敏感操作执行节点 */
    public static final String NEXT_SENSITIVE_OP = SensitiveOperationNode.NODE_NAME;

    /** 人工审核通过后继续到 Answer 节点 */
    public static final String NEXT_ANSWER = AnswerNode.NODE_NAME;

    /** 人工要求重写时回到 Planner 节点（重新规划后再 Prompt） */
    public static final String NEXT_PLANNER = PlannerNode.NODE_NAME;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> feedback = readFeedback(state);
        String nextNode = StateGraph.END;
        Map<String, Object> updates = new HashMap<>();

        if (feedback.isEmpty()) {
            updates.put(AgentGraphKeys.NEXT_NODE, NEXT_ANSWER);
            log.info("HumanNode completed, autoApproved=true, nextNode={}", NEXT_ANSWER);
            return updates;
        }

        boolean approved = Boolean.TRUE.equals(feedback.get("approved"));
        if (approved) {
            if (isDangerousOrderOp(state)) {
                nextNode = NEXT_SENSITIVE_OP;
            } else {
                nextNode = NEXT_ANSWER;
            }
        } else if (feedback.containsKey("revisedQuery")) {
            nextNode = NEXT_PLANNER;
            updates.put(AgentGraphKeys.QUERY, String.valueOf(feedback.get("revisedQuery")));
        } else if (isDangerousOrderOp(state)) {
            updates.put(AgentGraphKeys.ANSWER,
                    HumanApprovalDetector.buildCancelMessage(state.value(AgentGraphKeys.QUERY, "")));
            nextNode = NEXT_ANSWER;
        }

        updates.put(AgentGraphKeys.NEXT_NODE, nextNode);
        log.info("HumanNode completed, approved={}, nextNode={}", approved, nextNode);
        return updates;
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        if (!state.value(AgentGraphKeys.HUMAN_REVIEW_ENABLED, false)) {
            return Optional.empty();
        }
        if (state.value(AgentGraphKeys.HUMAN_FEEDBACK).isPresent()) {
            return Optional.empty();
        }
        String answer = state.value(AgentGraphKeys.ANSWER, "");
        if (answer.isBlank()) {
            return Optional.empty();
        }
        if (!shouldReview(state, answer)) {
            log.info("HumanNode skipped, no sensitive operation detected, query='{}'",
                    state.value(AgentGraphKeys.QUERY, ""));
            return Optional.empty();
        }
        String message = resolveReviewMessage(state, answer);
        InterruptionMetadata interruption = InterruptionMetadata.builder(nodeId, state)
                .addMetadata("message", message)
                .addMetadata("answer", answer)
                .addMetadata("query", state.value(AgentGraphKeys.QUERY, ""))
                .addMetadata("planStrategy", state.value(AgentGraphKeys.PLAN_STRATEGY, ""))
                .addMetadata("operationLabel",
                        HumanApprovalDetector.resolveOperationLabel(state.value(AgentGraphKeys.QUERY, "")))
                .build();
        log.info("HumanNode interrupted for sensitive operation, query='{}', answerLength={}",
                state.value(AgentGraphKeys.QUERY, ""), answer.length());
        return Optional.of(interruption);
    }

    private static boolean isDangerousOrderOp(OverAllState state) {
        return HumanApprovalDetector.isDangerousOrderOp(state.value(AgentGraphKeys.PLAN_STRATEGY, ""));
    }

    private static boolean shouldReview(OverAllState state, String answer) {
        return HumanApprovalDetector.requiresReview(
                state.value(AgentGraphKeys.HUMAN_REVIEW_ENABLED, false),
                state.value(AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, false),
                state.value(AgentGraphKeys.QUERY, ""),
                answer);
    }

    private static String resolveReviewMessage(OverAllState state, String answer) {
        String reason = state.value(AgentGraphKeys.APPROVAL_REASON, "");
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return HumanApprovalDetector.resolveReason(
                state.value(AgentGraphKeys.QUERY, ""), answer);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readFeedback(OverAllState state) {
        return state.value(AgentGraphKeys.HUMAN_FEEDBACK, Map.class).orElse(Map.of());
    }
}
