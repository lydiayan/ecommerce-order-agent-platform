package com.css.mallorderagent.planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanApprovalDetectorTest {

    @Test
    void informationalRefundQueryDoesNotRequireApproval() {
        assertFalse(HumanApprovalDetector.queryRequiresApproval("退款规则是什么"));
        assertFalse(HumanApprovalDetector.queryRequiresApproval("退货流程怎么操作"));
        assertFalse(HumanApprovalDetector.queryRequiresApproval("查询退款进度"));
        assertFalse(HumanApprovalDetector.queryRequiresApproval("退款到账了吗"));
        assertFalse(HumanApprovalDetector.queryRequiresApproval("退货进行到哪了"));
    }

    @Test
    void informationalExchangeAndAddressQueryDoesNotRequireApproval() {
        assertFalse(HumanApprovalDetector.queryRequiresApproval("换货流程是什么"));
        assertFalse(HumanApprovalDetector.queryRequiresApproval("改地址怎么操作"));
    }

    @Test
    void standaloneSensitiveTermsRequireApproval() {
        assertTrue(HumanApprovalDetector.queryRequiresApproval("退货"));
        assertTrue(HumanApprovalDetector.queryRequiresApproval("退款"));
        assertTrue(HumanApprovalDetector.queryRequiresApproval("换货"));
        assertTrue(HumanApprovalDetector.shouldAttachOrderContext("退货"));
        assertTrue(HumanApprovalDetector.shouldAttachOrderContext("退款"));
    }

    @Test
    void dangerousActionQueryRequiresApproval() {
        assertTrue(HumanApprovalDetector.queryRequiresApproval("我要申请退货"));
        assertTrue(HumanApprovalDetector.queryRequiresApproval("帮我取消订单 ORD123"));
        assertTrue(HumanApprovalDetector.queryRequiresApproval("确认付款"));
        assertTrue(HumanApprovalDetector.queryRequiresApproval("删除订单 ORD123"));
        assertTrue(HumanApprovalDetector.queryRequiresApproval("我要换货"));
        assertTrue(HumanApprovalDetector.queryRequiresApproval("帮我修改收货地址"));
    }

    @Test
    void confirmationAnswerRequiresApproval() {
        assertTrue(HumanApprovalDetector.answerRequiresApproval("您确认要申请退货吗？"));
        assertTrue(HumanApprovalDetector.answerRequiresApproval("是否确定取消该订单？"));
        assertTrue(HumanApprovalDetector.answerRequiresApproval("您确认要换货吗？"));
        assertTrue(HumanApprovalDetector.answerRequiresApproval("是否确定修改收货地址？"));
        assertFalse(HumanApprovalDetector.answerRequiresApproval("退款一般会在 3-5 个工作日到账。"));
    }

    @Test
    void requiresReviewRespectsGlobalSwitchAndPlanFlag() {
        assertFalse(HumanApprovalDetector.requiresReview(false, true, "我要退货", ""));
        assertTrue(HumanApprovalDetector.requiresReview(true, true, "查物流", ""));
        assertFalse(HumanApprovalDetector.requiresReview(true, false, "退款规则是什么", "退款一般会在 3-5 个工作日到账。"));
        assertTrue(HumanApprovalDetector.requiresReview(true, false, "我要换货", ""));
    }

    @Test
    void resolveOperationLabelFromQuery() {
        assertEquals("退货", HumanApprovalDetector.resolveOperationLabel("退货"));
        assertEquals("退款", HumanApprovalDetector.resolveOperationLabel("退款"));
    }

    @Test
    void orderQueryStyleAnswerTriggersApproval() {
        String answer = """
                确认退货吗？

                根据规则，该订单需满足以下条件方可无理由退货：
                - 签收后7天内；
                """;
        assertTrue(HumanApprovalDetector.answerRequiresApproval(answer));
        assertTrue(HumanApprovalDetector.requiresReview(true, false, "查询订单", answer));
    }

    @Test
    void parseUserConfirmationIntent() {
        assertEquals(HumanApprovalDetector.ConfirmationIntent.CONFIRM,
                HumanApprovalDetector.parseUserConfirmationIntent("确认"));
        assertEquals(HumanApprovalDetector.ConfirmationIntent.CONFIRM,
                HumanApprovalDetector.parseUserConfirmationIntent("好的，确认"));
        assertEquals(HumanApprovalDetector.ConfirmationIntent.CANCEL,
                HumanApprovalDetector.parseUserConfirmationIntent("取消"));
        assertEquals(HumanApprovalDetector.ConfirmationIntent.CANCEL,
                HumanApprovalDetector.parseUserConfirmationIntent("算了"));
        assertEquals(HumanApprovalDetector.ConfirmationIntent.UNKNOWN,
                HumanApprovalDetector.parseUserConfirmationIntent("查询我的订单"));
    }

    @Test
    void buildDangerousOrderConfirmationIncludesProduct() {
        String toolResult = """
                订单号：ORD20250101120000
                商品明细：
                - 手机 x1，单价 3999
                """;
        String msg = HumanApprovalDetector.buildDangerousOrderConfirmation("退货", toolResult, null);
        assertTrue(msg.contains("ORD20250101120000"));
        assertTrue(msg.contains("手机"));
        assertTrue(msg.contains("确认"));
        assertTrue(msg.contains("取消"));
    }
}
