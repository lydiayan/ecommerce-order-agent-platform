package com.example.mallorder.refund;

import java.util.List;

public record RefundEligibilityCommand(
        String userId,
        RefundReasonType reasonType,
        Boolean customerOpened,
        Boolean customerUsed,
        ProductConditionStatus conditionStatus,
        String reasonDescription,
        List<String> evidenceUrls
) {
    public RefundReasonType resolvedReasonType() {
        return reasonType != null ? reasonType : RefundReasonType.NO_REASON;
    }

    public List<String> resolvedEvidenceUrls() {
        return evidenceUrls != null ? List.copyOf(evidenceUrls) : List.of();
    }
}
