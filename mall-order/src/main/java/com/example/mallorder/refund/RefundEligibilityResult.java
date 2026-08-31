package com.example.mallorder.refund;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RefundEligibilityResult(
        String orderId,
        String userId,
        RefundDecision decision,
        RefundOperationType operationType,
        String policyVersion,
        List<String> reasonCodes,
        List<String> missingFields,
        RefundNextAction nextAction,
        BigDecimal refundableAmount,
        OffsetDateTime evaluatedAt,
        List<RefundItemResult> itemResults
) {
    /**
     * 判断资格结论是否允许进入售后工单流程；人工复核也允许先提交申请。
     *
     * @return 结论为可申请或人工复核时返回 {@code true}
     */
    public boolean canSubmitRequest() {
        return decision == RefundDecision.ELIGIBLE || decision == RefundDecision.MANUAL_REVIEW;
    }
}
