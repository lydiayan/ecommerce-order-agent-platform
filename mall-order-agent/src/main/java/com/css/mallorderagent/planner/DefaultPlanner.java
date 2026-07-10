package com.css.mallorderagent.planner;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认规划器：根据问题意图输出 {@link ActionDefinition} 动作链。
 */
@Component
public class DefaultPlanner implements Planner {

    private static final List<String> ORDER_KEYWORDS = List.of(
            "订单号", "订单编号", "查订单", "查询订单", "我的订单");

    private static final List<String> RAG_KEYWORDS = List.of(
            "订单", "物流", "配送", "退款", "退货", "售后", "发货", "签收", "时效", "补偿");

    @Override
    public PlanResult plan(String question) {
        if (question == null || question.isBlank()) {
            return new PlanResult("EMPTY", List.of());
        }

        String text = question.trim();
        boolean approvalRequired = HumanApprovalDetector.queryRequiresApproval(text);
        String approvalReason = approvalRequired ? HumanApprovalDetector.resolveReason(text, null) : null;

        // 敏感操作优先于普通订单查询（如「退货」「查询订单并退款」）
        if (approvalRequired) {
            boolean loadOrders = HumanApprovalDetector.shouldAttachOrderContext(text)
                    || ORDER_KEYWORDS.stream().anyMatch(text::contains);
            if (loadOrders) {
                return new PlanResult("DANGEROUS_ORDER_OP", ActionDefinitions.dangerousOrderPipeline(),
                        true, approvalReason);
            }
            return new PlanResult("DANGEROUS_OP", ActionDefinitions.ragQaPipeline(),
                    true, approvalReason);
        }

        if (ORDER_KEYWORDS.stream().anyMatch(text::contains)) {
            return new PlanResult("ORDER_QUERY", ActionDefinitions.orderQueryPipeline());
        }

        if (RAG_KEYWORDS.stream().anyMatch(text::contains)) {
            return new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline());
        }

        return new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline());
    }
}
