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
    /**
     * 返回明确的退款原因类型，未提供时按无理由场景处理。
     *
     * @return 非空退款原因类型
     */
    public RefundReasonType resolvedReasonType() {
        return reasonType != null ? reasonType : RefundReasonType.NO_REASON;
    }

    /**
     * 返回不可变的证据地址列表，未提供时返回空列表。
     *
     * @return 非空证据地址列表
     */
    public List<String> resolvedEvidenceUrls() {
        return evidenceUrls != null ? List.copyOf(evidenceUrls) : List.of();
    }
}
