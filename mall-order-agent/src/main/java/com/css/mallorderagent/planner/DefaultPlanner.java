package com.css.mallorderagent.planner;

import com.css.mallorderagent.config.OrderAgentProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/** 根据规则与受限模型分类结果生成动作链。 */
@Component
public class DefaultPlanner implements Planner {

    private static final String CLARIFICATION_MESSAGE =
            "我还不能确定您是想查询订单、咨询规则，还是执行退款、退货、取消等操作。"
                    + "请明确说明要查询的信息或要执行的操作。";

    private static final List<String> ORDER_KEYWORDS = List.of(
            "订单号", "订单编号", "查订单", "查询订单", "我的订单");

    private static final List<String> ORDER_QUERY_KEYWORDS = List.of(
            "查", "查询", "查看", "状态", "详情", "进度", "到哪");

    private static final List<String> AFTER_SALES_POLICY_KEYWORDS = List.of(
            "退款", "退货", "换货", "售后", "取消", "补偿");

    private static final List<String> POLICY_QUERY_KEYWORDS = List.of(
            "能否", "能不能", "是否能", "是否可以", "可以", "可不可以",
            "是否支持", "支持", "符合", "资格", "条件", "规则", "政策");

    private static final List<String> AFTER_SALES_STATUS_KEYWORDS = List.of(
            "进度", "到账", "到哪", "什么时候", "多久", "处理了吗", "完成了吗");

    private static final List<String> SENSITIVE_TOPICS = List.of(
            "退款", "退货", "换货", "取消", "付款", "支付", "删除", "改地址", "修改地址");

    private static final List<String> NEGATION_MARKERS = List.of(
            "不要", "别", "不用", "无需", "不想", "先不", "暂不");

    private static final List<String> MULTI_INTENT_MARKERS = List.of(
            "然后", "同时", "并且", "之后", "顺便", "再帮", "并帮");

    private static final Pattern ORDER_ID_PATTERN =
            Pattern.compile("ORD\\d{10,}", Pattern.CASE_INSENSITIVE);

    private static final List<String> RAG_KEYWORDS = List.of(
            "订单", "物流", "配送", "退款", "退货", "售后", "发货", "签收", "时效", "补偿");

    private final IntentClassifier intentClassifier;
    private final OrderAgentProperties.IntentProperties intentProperties;

    public DefaultPlanner(IntentClassifier intentClassifier, OrderAgentProperties properties) {
        this.intentClassifier = intentClassifier;
        this.intentProperties = properties.getIntent();
    }

    @Override
    public PlanResult plan(String question) {
        if (question == null || question.isBlank()) {
            PlanResult empty = new PlanResult("EMPTY", List.of());
            return applyClassification(empty, new IntentClassification(
                    IntentType.UNKNOWN, IntentSource.RULE, RuleMatchStatus.MATCH,
                    1D, false, "empty_query"));
        }

        String text = question.trim();
        RuleDecision rule = classifyByRules(text);
        IntentClassification classification = rule.status() == RuleMatchStatus.MATCH
                ? new IntentClassification(rule.intent(), IntentSource.RULE, rule.status(),
                        rule.confidence(), false, null)
                : classifyWithModel(text, rule);
        return buildPlan(text, classification);
    }

    private RuleDecision classifyByRules(String text) {
        boolean approvalRequired = HumanApprovalDetector.queryRequiresApproval(text);
        boolean hasOrderId = ORDER_ID_PATTERN.matcher(text).find();
        boolean hasAfterSalesTopic = containsAny(text, AFTER_SALES_POLICY_KEYWORDS);
        boolean hasPolicyQuery = containsAny(text, POLICY_QUERY_KEYWORDS);
        boolean hasAfterSalesStatus = containsAny(text, AFTER_SALES_STATUS_KEYWORDS);
        boolean hasOrderQuery = containsAny(text, ORDER_KEYWORDS)
                || (hasOrderId && containsAny(text, ORDER_QUERY_KEYWORDS));
        boolean hasSensitiveTopic = containsAny(text, SENSITIVE_TOPICS);
        boolean hasNegation = containsAny(text, NEGATION_MARKERS);
        boolean hasMultipleIntents = containsAny(text, MULTI_INTENT_MARKERS);

        // 否定式和“查询 + 执行”的混合表达不能直接触发敏感操作。
        if ((hasSensitiveTopic && hasNegation)
                || (approvalRequired && (hasOrderQuery || hasPolicyQuery
                || hasAfterSalesStatus || hasMultipleIntents))) {
            return new RuleDecision(RuleMatchStatus.AMBIGUOUS, IntentType.UNKNOWN,
                    0D, approvalRequired || hasSensitiveTopic, hasSensitiveTopic && hasNegation);
        }

        if (approvalRequired) {
            return new RuleDecision(RuleMatchStatus.MATCH,
                    IntentType.SENSITIVE_ORDER_OPERATION, 1D, true, false);
        }
        if (hasOrderId && hasAfterSalesTopic && hasPolicyQuery && !hasAfterSalesStatus) {
            return new RuleDecision(RuleMatchStatus.MATCH,
                    IntentType.ORDER_POLICY_QUERY, 0.98D, false, false);
        }
        if (hasOrderQuery) {
            return new RuleDecision(RuleMatchStatus.MATCH,
                    IntentType.ORDER_QUERY, 0.98D, false, false);
        }
        if (containsAny(text, RAG_KEYWORDS)) {
            return new RuleDecision(RuleMatchStatus.MATCH,
                    IntentType.RAG_QA, 0.95D, false, false);
        }
        return new RuleDecision(RuleMatchStatus.NO_MATCH,
                IntentType.UNKNOWN, 0D, false, false);
    }

    private IntentClassification classifyWithModel(String text, RuleDecision rule) {
        if (!intentProperties.isLlmEnabled()) {
            return clarification(rule, IntentSource.FALLBACK, "llm_disabled", 0D);
        }

        IntentModelDecision model;
        try {
            model = intentClassifier.classify(text);
        } catch (RuntimeException e) {
            model = IntentModelDecision.unknown("classifier_failure");
        }
        if (model == null) {
            model = IntentModelDecision.unknown("empty_model_result");
        }
        if (model.intent() == IntentType.UNKNOWN || model.clarificationRequired()
                || model.confidence() < intentProperties.getConfidenceThreshold()) {
            String reason = model.reasonCode() != null ? model.reasonCode() : "low_confidence";
            return clarification(rule, IntentSource.LLM, reason, model.confidence());
        }
        if (rule.negatedSensitive()) {
            return clarification(rule, IntentSource.LLM,
                    "negated_sensitive_intent", model.confidence());
        }
        // 模型不能凭空决定可执行操作；本地规则必须至少识别到敏感操作线索。
        if (model.intent() == IntentType.SENSITIVE_ORDER_OPERATION && !rule.riskDetected()) {
            return clarification(rule, IntentSource.LLM,
                    "sensitive_operation_unspecified", model.confidence());
        }
        if (rule.riskDetected() && model.intent() != IntentType.SENSITIVE_ORDER_OPERATION) {
            return clarification(rule, IntentSource.LLM,
                    "sensitive_rule_conflict", model.confidence());
        }
        return new IntentClassification(model.intent(), IntentSource.LLM, rule.status(),
                model.confidence(), false, model.reasonCode());
    }

    private static IntentClassification clarification(RuleDecision rule, IntentSource source,
                                                       String reason, double confidence) {
        return new IntentClassification(IntentType.UNKNOWN, source, rule.status(), confidence,
                true, reason);
    }

    private static PlanResult buildPlan(String text, IntentClassification classification) {
        PlanResult plan;
        switch (classification.intent()) {
            case ORDER_QUERY -> plan = new PlanResult(
                    "ORDER_QUERY", ActionDefinitions.orderQueryPipeline());
            case ORDER_POLICY_QUERY -> plan = new PlanResult(
                    "ORDER_POLICY_QUERY", ActionDefinitions.orderPolicyQueryPipeline());
            case RAG_QA -> plan = new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline());
            case SENSITIVE_ORDER_OPERATION -> {
                boolean loadOrders = classification.source() == IntentSource.LLM
                        || HumanApprovalDetector.shouldAttachOrderContext(text)
                        || containsAny(text, ORDER_KEYWORDS);
                String reason = HumanApprovalDetector.resolveReason(text, null);
                plan = loadOrders
                        ? new PlanResult("DANGEROUS_ORDER_OP",
                                ActionDefinitions.dangerousOrderPipeline(), true, reason)
                        : new PlanResult("DANGEROUS_OP",
                                ActionDefinitions.ragQaPipeline(), true, reason);
            }
            case UNKNOWN -> {
                plan = new PlanResult("CLARIFY_INTENT", List.of());
                plan.setClarificationMessage(CLARIFICATION_MESSAGE);
            }
            default -> throw new IllegalStateException("Unsupported intent " + classification.intent());
        }
        return applyClassification(plan, classification);
    }

    private static PlanResult applyClassification(PlanResult plan,
                                                   IntentClassification classification) {
        plan.setIntent(classification.intent().name());
        plan.setIntentSource(classification.source().name());
        plan.setIntentConfidence(classification.confidence());
        plan.setRuleMatchStatus(classification.ruleMatchStatus().name());
        plan.setClarificationRequired(classification.clarificationRequired());
        plan.setClassificationFallbackReason(classification.fallbackReason());
        return plan;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private record RuleDecision(RuleMatchStatus status, IntentType intent,
                                double confidence, boolean riskDetected,
                                boolean negatedSensitive) {
    }
}
