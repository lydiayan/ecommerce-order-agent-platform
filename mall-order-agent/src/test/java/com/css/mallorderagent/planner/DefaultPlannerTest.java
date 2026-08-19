package com.css.mallorderagent.planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPlannerTest {

    private final DefaultPlanner planner = new DefaultPlanner();

    @Test
    void normalRagQuestionDoesNotRequireApproval() {
        PlanResult plan = planner.plan("退款规则是什么");
        assertFalse(plan.humanApprovalRequired());
    }

    @Test
    void standaloneReturnAndRefundUseDangerousOrderOp() {
        PlanResult refund = planner.plan("退款");
        assertTrue(refund.humanApprovalRequired());
        assertEquals("DANGEROUS_ORDER_OP", refund.strategy());
        assertEquals(4, refund.actions().size());

        PlanResult returnPlan = planner.plan("退货");
        assertTrue(returnPlan.humanApprovalRequired());
        assertEquals("DANGEROUS_ORDER_OP", returnPlan.strategy());
    }

    @Test
    void confirmationFallbackUsesOrderId() {
        String tool = "用户 USER1005 共有 1 笔订单：\n[1] ORD20250414005 已完成 5499.00";
        String answer = HumanApprovalDetector.buildDangerousOrderConfirmation("退货", tool, null);
        assertTrue(answer.contains("ORD20250414005"));
        assertTrue(answer.contains("退"));
        assertTrue(answer.contains("吗"));
        assertTrue(answer.contains("确认"));
        assertFalse(answer.contains("您的订单信息如下"));
    }

    @Test
    void normalOrderQueryDoesNotRequireApproval() {
        PlanResult plan = planner.plan("查询我的订单");
        assertFalse(plan.humanApprovalRequired());
        assertEquals("ORDER_QUERY", plan.strategy());
    }

    @Test
    void orderIdAndQueryIntentTolerateWordsBetweenQueryAndOrder() {
        PlanResult plan = planner.plan("查询虚构订单 ORD20260810001 的状态");
        assertFalse(plan.humanApprovalRequired());
        assertEquals("ORDER_QUERY", plan.strategy());
    }

    @Test
    void orderRefundEligibilityUsesOrderFactsAndPolicyKnowledge() {
        PlanResult plan = planner.plan("ORD20260810001 是否可以退款");

        assertFalse(plan.humanApprovalRequired());
        assertEquals("ORDER_POLICY_QUERY", plan.strategy());
        assertEquals(3, plan.actions().size());
        assertTrue(plan.hasAction(ActionDefinitions.REFUND_ELIGIBILITY));
        assertFalse(plan.hasAction(ActionDefinitions.ORDER_QUERY));
        assertFalse(plan.hasAction(ActionDefinitions.KNOWLEDGE_SEARCH));
    }

    @Test
    void explicitOrderQueryWithRefundEligibilityStillUsesCombinedPolicyStrategy() {
        PlanResult plan = planner.plan("查询订单 ORD20260810001 可以退款吗");

        assertEquals("ORDER_POLICY_QUERY", plan.strategy());
        assertFalse(plan.humanApprovalRequired());
    }

    @Test
    void explicitRefundActionWithOrderIdRemainsDangerous() {
        PlanResult plan = planner.plan("帮我退款 ORD20260810001");

        assertEquals("DANGEROUS_ORDER_OP", plan.strategy());
        assertTrue(plan.humanApprovalRequired());
    }

    @Test
    void refundPolicyWithoutOrderIdRemainsRagOnly() {
        PlanResult plan = planner.plan("退款规则是什么");

        assertEquals("RAG_QA", plan.strategy());
        assertFalse(plan.hasAction(ActionDefinitions.ORDER_QUERY));
    }

    @Test
    void exchangeAndAddressRequireApproval() {
        assertTrue(planner.plan("我要换货").humanApprovalRequired());
        assertTrue(planner.plan("帮我改收货地址").humanApprovalRequired());
        assertFalse(planner.plan("换货政策是什么").humanApprovalRequired());
    }

    @Test
    void refundProgressQueryUsesRagNotDangerousOp() {
        PlanResult plan = planner.plan("查询退款进度");
        assertFalse(plan.humanApprovalRequired());
        assertEquals("RAG_QA", plan.strategy());
    }

    @Test
    void refundProgressWithOrderIdDoesNotBecomeEligibilityQuery() {
        PlanResult plan = planner.plan("查询 ORD20260810001 的退款到账进度");

        assertFalse(plan.humanApprovalRequired());
        assertEquals("ORDER_QUERY", plan.strategy());
    }
}
