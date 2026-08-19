package com.css.mallorderagent.planner;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 默认规划器：根据问题意图输出 {@link ActionDefinition} 动作链。
 */
@Component
public class DefaultPlanner implements Planner {

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

    private static final Pattern ORDER_ID_PATTERN =
            Pattern.compile("ORD\\d{10,}", Pattern.CASE_INSENSITIVE);

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

        boolean hasOrderId = ORDER_ID_PATTERN.matcher(text).find();
        boolean hasAfterSalesPolicyTopic = AFTER_SALES_POLICY_KEYWORDS.stream().anyMatch(text::contains);
        boolean hasPolicyQueryIntent = POLICY_QUERY_KEYWORDS.stream().anyMatch(text::contains);
        boolean hasAfterSalesStatusIntent = AFTER_SALES_STATUS_KEYWORDS.stream().anyMatch(text::contains);
        if (hasOrderId && hasAfterSalesPolicyTopic && hasPolicyQueryIntent && !hasAfterSalesStatusIntent) {
            return new PlanResult("ORDER_POLICY_QUERY", ActionDefinitions.orderPolicyQueryPipeline());
        }

        boolean hasOrderQueryIntent = ORDER_QUERY_KEYWORDS.stream().anyMatch(text::contains);
        if (ORDER_KEYWORDS.stream().anyMatch(text::contains)
                || (hasOrderId && hasOrderQueryIntent)) {
            return new PlanResult("ORDER_QUERY", ActionDefinitions.orderQueryPipeline());
        }

        if (RAG_KEYWORDS.stream().anyMatch(text::contains)) {
            return new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline());
        }

        return new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline());
    }
}
