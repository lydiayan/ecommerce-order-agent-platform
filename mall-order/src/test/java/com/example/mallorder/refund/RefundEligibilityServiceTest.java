package com.example.mallorder.refund;

import com.example.mallorder.entity.Order;
import com.example.mallorder.entity.OrderDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundEligibilityServiceTest {

    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-18T04:00:00Z"), CHINA);
    private final RefundEligibilityService service = new RefundEligibilityService(CLOCK);

    @Test
    void paidOrdinaryOrderNotShippedIsEligibleWithoutReceiptFacts() {
        Order order = order(1, 0, 0);

        RefundEligibilityResult result = service.evaluate(order, command());

        assertEquals(RefundDecision.ELIGIBLE, result.decision());
        assertEquals(RefundOperationType.REFUND_ONLY, result.operationType());
        assertEquals(List.of("PAID_AND_NOT_SHIPPED"), result.reasonCodes());
        assertTrue(result.missingFields().isEmpty());
    }

    @Test
    void orderInTransitRequiresManualReview() {
        Order order = order(2, 1, 0);

        RefundEligibilityResult result = service.evaluate(order, command());

        assertEquals(RefundDecision.MANUAL_REVIEW, result.decision());
        assertEquals(RefundNextAction.REQUEST_LOGISTICS_INTERCEPT, result.nextAction());
    }

    @Test
    void signedOrderOutsideSevenDayWindowIsIneligible() {
        Order order = order(3, 2, 0);
        order.setSignedAt(LocalDateTime.of(2026, 8, 10, 9, 0));

        RefundEligibilityResult result = service.evaluate(order, command(false, ProductConditionStatus.RESALABLE));

        assertEquals(RefundDecision.INELIGIBLE, result.decision());
        assertTrue(result.reasonCodes().contains("RETURN_WINDOW_EXPIRED"));
    }

    @Test
    void exactSevenDayBoundaryIsIncluded() {
        Order order = order(3, 2, 0);
        order.setSignedAt(LocalDateTime.of(2026, 8, 11, 12, 0));

        RefundEligibilityResult result = service.evaluate(order, command(false, ProductConditionStatus.RESALABLE));

        assertEquals(RefundDecision.ELIGIBLE, result.decision());
        assertTrue(result.reasonCodes().contains("WITHIN_SEVEN_DAYS"));
    }

    @Test
    void signedOrdinaryOrderOnlyRequestsMissingConditionFacts() {
        Order order = order(3, 2, 0);
        order.setSignedAt(LocalDateTime.of(2026, 8, 17, 12, 0));

        RefundEligibilityResult result = service.evaluate(order, command());

        assertEquals(RefundDecision.NEED_MORE_INFO, result.decision());
        assertEquals(List.of("customerUsed", "conditionStatus"), result.missingFields());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void signedExcludedProductTypesDoNotAskForCondition(int productType) {
        Order order = order(3, 2, productType);
        order.setSignedAt(LocalDateTime.of(2026, 8, 17, 12, 0));

        RefundEligibilityResult result = service.evaluate(order, command());

        assertEquals(RefundDecision.INELIGIBLE, result.decision());
        assertEquals(List.of("PRODUCT_TYPE_EXCLUDED_FROM_NO_REASON_RETURN"), result.reasonCodes());
        assertTrue(result.missingFields().isEmpty());
    }

    @Test
    void qualityIssueCanBeSubmittedForExcludedProductTypeWithEvidence() {
        Order order = order(3, 2, 2);
        RefundEligibilityCommand command = new RefundEligibilityCommand(
                "USER1001", RefundReasonType.QUALITY_ISSUE, true, true, null,
                "收到的生鲜商品已经损坏", List.of("https://evidence.invalid/photo-1"));

        RefundEligibilityResult result = service.evaluate(order, command);

        assertEquals(RefundDecision.ELIGIBLE, result.decision());
        assertTrue(result.reasonCodes().contains("QUALITY_ISSUE"));
    }

    @Test
    void mixedProductTypesAreReportedAsInconsistent() {
        Order order = order(1, 0, 0);
        OrderDetail second = detail(2, 1);
        order.setOrderDetails(List.of(order.getOrderDetails().get(0), second));

        RefundEligibilityResult result = service.evaluate(order, command());

        assertEquals(RefundDecision.NEED_MORE_INFO, result.decision());
        assertTrue(result.reasonCodes().contains("DATA_INCONSISTENT"));
    }

    private static Order order(int orderStatus, int deliveryStatus, int productType) {
        Order order = new Order();
        order.setOrderId("ORD20260810001");
        order.setUserId("USER1001");
        order.setOrderStatus(orderStatus);
        order.setDeliveryStatus(deliveryStatus);
        order.setTotalAmount(new BigDecimal("5999.00"));
        order.setOrderDetails(List.of(detail(1, productType)));
        return order;
    }

    private static OrderDetail detail(int id, int productType) {
        OrderDetail detail = new OrderDetail();
        detail.setDetailId(id);
        detail.setProductType(productType);
        return detail;
    }

    private static RefundEligibilityCommand command() {
        return command(null, null);
    }

    private static RefundEligibilityCommand command(Boolean used, ProductConditionStatus condition) {
        return new RefundEligibilityCommand(
                "USER1001", RefundReasonType.NO_REASON, null, used, condition, null, List.of());
    }
}
