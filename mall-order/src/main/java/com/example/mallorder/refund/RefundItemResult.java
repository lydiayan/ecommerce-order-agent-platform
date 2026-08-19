package com.example.mallorder.refund;

import java.util.List;

public record RefundItemResult(
        Integer detailId,
        Integer productType,
        RefundDecision decision,
        List<String> reasonCodes
) {
}
