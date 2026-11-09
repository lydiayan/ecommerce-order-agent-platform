package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
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
    static final String REVIEW_TRACE_OPERATION = "human.review";

    /** 人工审核通过后路由到的敏感操作执行节点 */
    public static final String NEXT_SENSITIVE_OP = SensitiveOperationNode.NODE_NAME;

    /** 人工审核通过后继续到 Answer 节点 */
    public static final String NEXT_ANSWER = AnswerNode.NODE_NAME;

    /** 人工要求重写时回到 Planner 节点（重新规划后再 Prompt） */
    public static final String NEXT_PLANNER = PlannerNode.NODE_NAME;

    /**
     * 消费恢复 Graph 时写入的人工反馈，并决定进入执行、回答、重新规划或结束。
     *
     * @param state 包含审核反馈、计划策略和当前回答的 Graph 状态
     * @return 下一节点、修订问题或取消提示等状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> startAttributes = traceAttributes(state);
        RagTraceScope trace = RagTracingAdvisor.parentScope();
        try (RagTraceScope humanSpan = trace.child(NODE_NAME, startAttributes)) {
            try {
                Map<String, Object> feedback = readFeedback(state);
                humanSpan.attribute("feedbackPresent", !feedback.isEmpty());
                String nextNode = StateGraph.END;
                Map<String, Object> updates = new HashMap<>();

                if (feedback.isEmpty()) {
                    updates.put(AgentGraphKeys.NEXT_NODE, NEXT_ANSWER);
                    humanSpan.attribute("autoApproved", true);
                    humanSpan.attribute("nextNode", NEXT_ANSWER);
                    log.info("HumanNode completed, autoApproved=true, nextNode={}", NEXT_ANSWER);
                    return updates;
                }

                boolean approved = Boolean.TRUE.equals(feedback.get("approved"));
                boolean revisedQueryProvided = feedback.containsKey("revisedQuery");
                if (approved) {
                    if (isDangerousOrderOp(state)) {
                        nextNode = NEXT_SENSITIVE_OP;
                    } else {
                        nextNode = NEXT_ANSWER;
                    }
                } else if (revisedQueryProvided) {
                    nextNode = NEXT_PLANNER;
                    updates.put(AgentGraphKeys.QUERY, String.valueOf(feedback.get("revisedQuery")));
                } else if (isDangerousOrderOp(state)) {
                    updates.put(AgentGraphKeys.ANSWER,
                            HumanApprovalDetector.buildCancelMessage(state.value(AgentGraphKeys.QUERY, "")));
                    nextNode = NEXT_ANSWER;
                }

                updates.put(AgentGraphKeys.NEXT_NODE, nextNode);
                humanSpan.attribute("approved", approved);
                humanSpan.attribute("revisedQueryProvided", revisedQueryProvided);
                humanSpan.attribute("nextNode", nextNode);
                log.info("HumanNode completed, approved={}, nextNode={}", approved, nextNode);
                return updates;
            } catch (RuntimeException e) {
                humanSpan.error(e);
                throw e;
            }
        }
    }

    /**
     * 在需要人工审核且尚无反馈时中断 Graph，并构造前端恢复所需元数据。
     *
     * @param nodeId 当前 Graph 节点编号
     * @param state 包含回答、问题、策略和审批标记的 Graph 状态
     * @param config 当前 Graph 运行配置
     * @return 需要暂停时返回中断元数据，否则返回 empty
     */
    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        Map<String, Object> startAttributes = traceAttributes(state);
        RagTraceScope trace = RagTracingAdvisor.parentScope();
        try (RagTraceScope reviewSpan = trace.child(REVIEW_TRACE_OPERATION, startAttributes)) {
            try {
                if (!state.value(AgentGraphKeys.HUMAN_REVIEW_ENABLED, false)) {
                    return noReview(reviewSpan, "review_disabled");
                }
                if (!readFeedback(state).isEmpty()) {
                    return noReview(reviewSpan, "feedback_already_present");
                }
                String answer = state.value(AgentGraphKeys.ANSWER, "");
                reviewSpan.attribute("answerLength", answer.length());
                if (answer.isBlank()) {
                    return noReview(reviewSpan, "answer_missing");
                }
                if (!shouldReview(state, answer)) {
                    log.info("HumanNode skipped, no sensitive operation detected, queryLength={}",
                            state.value(AgentGraphKeys.QUERY, "").length());
                    return noReview(reviewSpan, "review_not_required");
                }
                String query = state.value(AgentGraphKeys.QUERY, "");
                String operationLabel = HumanApprovalDetector.resolveOperationLabel(query);
                String message = resolveReviewMessage(state, answer);
                InterruptionMetadata interruption = InterruptionMetadata.builder(nodeId, state)
                        .addMetadata("message", message)
                        .addMetadata("answer", answer)
                        .addMetadata("query", query)
                        .addMetadata("planStrategy", state.value(AgentGraphKeys.PLAN_STRATEGY, ""))
                        .addMetadata("operationLabel", operationLabel)
                        .build();
                reviewSpan.attribute("reviewRequired", true);
                reviewSpan.attribute("decisionReason", "sensitive_operation");
                reviewSpan.attribute("operationLabel", operationLabel);
                log.info("HumanNode interrupted for sensitive operation, queryLength={}, answerLength={}",
                        query.length(), answer.length());
                return Optional.of(interruption);
            } catch (RuntimeException e) {
                reviewSpan.error(e);
                throw e;
            }
        }
    }

    private static Optional<InterruptionMetadata> noReview(RagTraceScope reviewSpan, String reason) {
        reviewSpan.attribute("reviewRequired", false);
        reviewSpan.attribute("decisionReason", reason);
        return Optional.empty();
    }

    private static Map<String, Object> traceAttributes(OverAllState state) {
        Map<String, Object> attributes = new HashMap<>();
        String conversationId = state.value(AgentGraphKeys.SESSION_ID, "");
        if (!conversationId.isBlank()) {
            attributes.put("conversationId", conversationId);
        }
        attributes.put("planStrategy", state.value(AgentGraphKeys.PLAN_STRATEGY, ""));
        attributes.put("dangerousOperation", isDangerousOrderOp(state));
        attributes.put("humanReviewEnabled", state.value(AgentGraphKeys.HUMAN_REVIEW_ENABLED, false));
        attributes.put("feedbackPresent", !readFeedback(state).isEmpty());
        return attributes;
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
