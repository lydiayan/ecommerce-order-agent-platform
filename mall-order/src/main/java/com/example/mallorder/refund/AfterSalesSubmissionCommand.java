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
    /**
     * 提取退款资格评估所需字段，忽略售后操作类型。
     *
     * @return 对应的资格评估命令
     */
    public RefundEligibilityCommand toEligibilityCommand() {
        return new RefundEligibilityCommand(userId, reasonType, customerOpened, customerUsed,
                conditionStatus, reasonDescription, evidenceUrls);
    }
}
