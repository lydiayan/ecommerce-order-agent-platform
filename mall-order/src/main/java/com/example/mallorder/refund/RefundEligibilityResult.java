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
    public boolean canSubmitRequest() {
        return decision == RefundDecision.ELIGIBLE || decision == RefundDecision.MANUAL_REVIEW;
    }
}
