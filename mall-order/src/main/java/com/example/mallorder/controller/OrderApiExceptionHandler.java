package com.example.mallorder.controller;

import com.example.mallorder.refund.AfterSalesRejectionException;
import com.example.mallorder.refund.RefundEligibilityResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderApiExceptionHandler {

    static final String BUSINESS_REJECTION = "BUSINESS_REJECTION";
    static final String AFTER_SALES_NOT_ELIGIBLE = "AFTER_SALES_NOT_ELIGIBLE";

    @ExceptionHandler(AfterSalesRejectionException.class)
    public ResponseEntity<AfterSalesRejectionResponse> handleAfterSalesRejection(
            AfterSalesRejectionException exception) {
        RefundEligibilityResult eligibility = exception.eligibility();
        AfterSalesRejectionResponse body = new AfterSalesRejectionResponse(
                BUSINESS_REJECTION,
                AFTER_SALES_NOT_ELIGIBLE,
                rejectionMessage(eligibility),
                nameOf(eligibility.decision()),
                nameOf(eligibility.operationType()),
                safeList(eligibility.reasonCodes()),
                safeList(eligibility.missingFields()),
                nameOf(eligibility.nextAction()),
                eligibility.policyVersion());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    static String rejectionMessage(RefundEligibilityResult eligibility) {
        List<String> reasons = safeList(eligibility.reasonCodes());
        if (reasons.contains("RETURN_WINDOW_EXPIRED")) {
            return "该订单已超过7天退货期限";
        }
        if (reasons.contains("ORDER_UNPAID")) {
            return "订单尚未付款，不能提交退款申请";
        }
        if (reasons.contains("ORDER_CANCELLED")) {
            return "订单已取消，不能重复提交售后申请";
        }
        if (reasons.contains("PRODUCT_TYPE_EXCLUDED_FROM_NO_REASON_RETURN")) {
            return "该商品类型不支持无理由退货";
        }
        if (reasons.contains("VIRTUAL_PRODUCT_ALREADY_DELIVERED")) {
            return "虚拟商品已交付，不支持无理由退款";
        }
        if (reasons.contains("PRODUCT_USED")) {
            return "商品已使用，不符合无理由退货条件";
        }
        if (reasons.contains("PRODUCT_NOT_RESALABLE")) {
            return "商品状态不满足再次销售条件";
        }
        if (!safeList(eligibility.missingFields()).isEmpty()) {
            return "需要补充售后申请信息：" + String.join("、", eligibility.missingFields());
        }
        if (!reasons.isEmpty()) {
            return "订单暂不符合售后申请条件（" + String.join("、", reasons) + "）";
        }
        return "订单暂不符合售后申请条件";
    }

    private static List<String> safeList(List<String> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    private static String nameOf(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    public record AfterSalesRejectionResponse(
            String errorType,
            String code,
            String message,
            String decision,
            String operationType,
            List<String> reasonCodes,
            List<String> missingFields,
            String nextAction,
            String policyVersion) {
    }
}
