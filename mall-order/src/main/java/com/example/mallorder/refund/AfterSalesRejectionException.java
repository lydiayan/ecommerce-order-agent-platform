package com.example.mallorder.refund;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 售后申请被权威订单规则拒绝，而非订单服务发生技术故障。
 */
public final class AfterSalesRejectionException extends ResponseStatusException {

    private final RefundEligibilityResult eligibility;

    public AfterSalesRejectionException(RefundEligibilityResult eligibility) {
        super(HttpStatus.CONFLICT, "after-sales request rejected by eligibility policy");
        this.eligibility = eligibility;
    }

    public RefundEligibilityResult eligibility() {
        return eligibility;
    }
}
