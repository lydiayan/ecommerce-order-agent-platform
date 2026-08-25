package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.planner.Planner;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.prompt.BuiltPrompt;
import com.css.mallorderagent.demo.DemoCapability;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规划节点：根据用户问题决定策略（如是否走 RAG、调用哪些工具）。
 */
@Component
public class PlannerNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);

    public static final String NODE_NAME = "planner";

    private final Planner planner;

    public PlannerNode(Planner planner) {
        this.planner = planner;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        Map<String, Object> startAttributes = new LinkedHashMap<>();
        startAttributes.put("queryLength", query.length());
        startAttributes.put("queryFingerprint", TracePrivacy.fingerprint(query));
        String conversationId = state.value(AgentGraphKeys.SESSION_ID, "");
        if (!conversationId.isBlank()) {
            startAttributes.put("conversationId", conversationId);
        }

        RagTraceScope trace = RagTracingAdvisor.parentScope();
        try (RagTraceScope plannerSpan = trace.child(NODE_NAME, startAttributes)) {
            try {
                PlanResult plan = planner.plan(query);
                String denial = capabilityDenial(state, plan, query);
                if (denial != null) {
                    PlanResult denied = new PlanResult("CAPABILITY_DENIED", List.of());
                    copyClassification(plan, denied);
                    Map<String, Object> updates = resetTurnOutputs(query);
                    updates.put(AgentGraphKeys.PLAN, denied);
                    updates.put(AgentGraphKeys.PLAN_STRATEGY, denied.strategy());
                    putClassification(updates, denied);
                    updates.put(AgentGraphKeys.ANSWER, denial);
                    return updates;
                }
                List<String> actions = plan.actions().stream()
                        .map(action -> action.action())
                        .toList();

                plannerSpan.attribute("planStrategy", plan.strategy());
                plannerSpan.attribute("actionCount", actions.size());
                plannerSpan.attribute("actions", actions);
                plannerSpan.attribute("humanApprovalRequired", plan.humanApprovalRequired());
                plannerSpan.attribute("intent", valueOrEmpty(plan.intent()));
                plannerSpan.attribute("intentSource", valueOrEmpty(plan.intentSource()));
                plannerSpan.attribute("intentConfidence", plan.intentConfidence());
                plannerSpan.attribute("ruleMatchStatus", valueOrEmpty(plan.ruleMatchStatus()));
                plannerSpan.attribute("clarificationRequired", plan.clarificationRequired());
                if (plan.approvalReason() != null && !plan.approvalReason().isBlank()) {
                    plannerSpan.attribute("approvalReason", plan.approvalReason());
                }
                if (plan.classificationFallbackReason() != null
                        && !plan.classificationFallbackReason().isBlank()) {
                    plannerSpan.attribute("classificationFallbackReason",
                            plan.classificationFallbackReason());
                }

                log.info("PlannerNode completed, strategy={}, actions={}, humanApproval={}",
                        plan.strategy(), plan.actions(), plan.humanApprovalRequired());

                Map<String, Object> updates = resetTurnOutputs(query);
                updates.put(AgentGraphKeys.PLAN, plan);
                updates.put(AgentGraphKeys.PLAN_STRATEGY, plan.strategy());
                putClassification(updates, plan);
                updates.put(AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, plan.humanApprovalRequired());
                updates.put(AgentGraphKeys.APPROVAL_REASON,
                        plan.approvalReason() != null ? plan.approvalReason() : "");
                if (plan.clarificationRequired() && plan.clarificationMessage() != null) {
                    updates.put(AgentGraphKeys.ANSWER, plan.clarificationMessage());
                }
                return updates;
            } catch (RuntimeException e) {
                plannerSpan.error(e);
                throw e;
            }
        }
    }

    private static Map<String, Object> resetTurnOutputs(String query) {
        // Graph checkpoints span a conversation, so derived values must not leak into the next turn.
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(AgentGraphKeys.RETRIEVAL, new SearchResponse(query, 0, List.of()));
        updates.put(AgentGraphKeys.CONTEXT, "");
        updates.put(AgentGraphKeys.CONTEXT_HIT_COUNT, 0);
        updates.put(AgentGraphKeys.GROUNDED, false);
        updates.put(AgentGraphKeys.BUILT_PROMPT, new BuiltPrompt("", ""));
        updates.put(AgentGraphKeys.TOOL_RESULT, "");
        updates.put(AgentGraphKeys.ANSWER, "");
        updates.put(AgentGraphKeys.HUMAN_FEEDBACK, Map.of());
        updates.put(AgentGraphKeys.NEXT_NODE, "");
        updates.put(AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, false);
        updates.put(AgentGraphKeys.APPROVAL_REASON, "");
        updates.put(AgentGraphKeys.INTENT, "");
        updates.put(AgentGraphKeys.INTENT_SOURCE, "");
        updates.put(AgentGraphKeys.INTENT_CONFIDENCE, 0D);
        updates.put(AgentGraphKeys.RULE_MATCH_STATUS, "");
        updates.put(AgentGraphKeys.CLARIFICATION_REQUIRED, false);
        return updates;
    }

    private static void putClassification(Map<String, Object> updates, PlanResult plan) {
        updates.put(AgentGraphKeys.INTENT, valueOrEmpty(plan.intent()));
        updates.put(AgentGraphKeys.INTENT_SOURCE, valueOrEmpty(plan.intentSource()));
        updates.put(AgentGraphKeys.INTENT_CONFIDENCE, plan.intentConfidence());
        updates.put(AgentGraphKeys.RULE_MATCH_STATUS, valueOrEmpty(plan.ruleMatchStatus()));
        updates.put(AgentGraphKeys.CLARIFICATION_REQUIRED, plan.clarificationRequired());
    }

    private static void copyClassification(PlanResult source, PlanResult target) {
        target.setIntent(source.intent());
        target.setIntentSource(source.intentSource());
        target.setIntentConfidence(source.intentConfidence());
        target.setRuleMatchStatus(source.ruleMatchStatus());
        target.setClarificationRequired(source.clarificationRequired());
        target.setClassificationFallbackReason(source.classificationFallbackReason());
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private static String capabilityDenial(OverAllState state, PlanResult plan, String query) {
        if (!AgentGraphSupport.hasCapabilityContext(state)) {
            return null;
        }
        if ("ORDER_QUERY".equals(plan.strategy())
                || "ORDER_POLICY_QUERY".equals(plan.strategy())
                || "DANGEROUS_ORDER_OP".equals(plan.strategy())) {
            boolean canRead = AgentGraphSupport.hasCapability(state, DemoCapability.OWN_ORDER_READ.name())
                    || AgentGraphSupport.hasCapability(state, DemoCapability.ASSIGNED_ORDER_READ.name());
            if (!canRead) {
                return "当前演示身份没有订单查询能力。请切换到销售或客户身份后重试。";
            }
        }
        if (HumanApprovalDetector.isDangerousOrderOp(plan.strategy())) {
            String operation = HumanApprovalDetector.resolveOperationLabel(query);
            boolean cancel = operation.contains("取消");
            boolean allowed = AgentGraphSupport.hasCapability(state,
                    (cancel ? DemoCapability.ORDER_CANCEL : DemoCapability.AFTER_SALES_CREATE).name());
            if (!allowed) {
                return "当前演示身份只能查看已授权订单，不能执行取消或售后操作。";
            }
        }
        if (plan.needRag() && !AgentGraphSupport.hasCapability(state, DemoCapability.KNOWLEDGE_SEARCH.name())) {
            return "当前演示身份没有知识检索能力。";
        }
        return null;
    }
}
