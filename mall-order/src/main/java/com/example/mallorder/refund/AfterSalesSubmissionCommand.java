package com.example.mallorder.refund;

import java.util.List;

public record AfterSalesSubmissionCommand(
        String userId,
        String operationType,
        RefundReasonType reasonType,
        Boolean customerOpened,
        Boolean customerUsed,
        ProductConditionStatus conditionStatus,
        String reasonDescription,
        List<String> evidenceUrls
) {
    public RefundEligibilityCommand toEligibilityCommand() {
        return new RefundEligibilityCommand(userId, reasonType, customerOpened, customerUsed,
                conditionStatus, reasonDescription, evidenceUrls);
    }
}
