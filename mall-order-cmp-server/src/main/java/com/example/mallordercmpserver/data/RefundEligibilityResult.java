package com.example.mallordercmpserver.data;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class RefundEligibilityResult {
    private String orderId;
    private String userId;
    private String decision;
    private String operationType;
    private String policyVersion;
    private List<String> reasonCodes;
    private List<String> missingFields;
    private String nextAction;
    private BigDecimal refundableAmount;
    private OffsetDateTime evaluatedAt;
    private List<RefundItemResult> itemResults;

    @Data
    public static class RefundItemResult {
        private Integer detailId;
        private Integer productType;
        private String decision;
        private List<String> reasonCodes;
    }
}
