package com.example.mallorder.refund;

import com.example.mallorder.entity.Order;
import com.example.mallorder.entity.OrderDetail;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RefundEligibilityService {

    public static final String POLICY_VERSION = "refund-v2026.08.18";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final Clock clock;

    public RefundEligibilityService(Clock clock) {
        this.clock = clock;
    }

    public RefundEligibilityResult evaluate(Order order, RefundEligibilityCommand command) {
        RefundEligibilityCommand resolvedCommand = command != null
                ? command
                : new RefundEligibilityCommand(order.getUserId(), null, null, null, null, null, List.of());
        List<OrderDetail> details = order.getOrderDetails() != null ? order.getOrderDetails() : List.of();

        if (details.isEmpty()) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, null,
                    List.of("MISSING_ORDER_DETAILS"), List.of("orderDetails"),
                    RefundNextAction.PROVIDE_INFORMATION);
        }

        Set<Integer> productTypeCodes = new LinkedHashSet<>();
        for (OrderDetail detail : details) {
            productTypeCodes.add(detail.getProductType());
        }
        if (productTypeCodes.contains(null) || productTypeCodes.size() != 1) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, null,
                    List.of("DATA_INCONSISTENT", "MIXED_OR_UNKNOWN_PRODUCT_TYPE"),
                    List.of("orderDetails.productType"), RefundNextAction.CONTACT_SUPPORT);
        }
        ProductType productType = ProductType.fromCode(productTypeCodes.iterator().next()).orElse(null);
        if (productType == null) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, null,
                    List.of("DATA_INCONSISTENT", "MIXED_OR_UNKNOWN_PRODUCT_TYPE"),
                    List.of("orderDetails.productType"), RefundNextAction.CONTACT_SUPPORT);
        }

        OrderStatus orderStatus = OrderStatus.fromCode(order.getOrderStatus()).orElse(null);
        if (orderStatus == null) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, null,
                    List.of("DATA_INCONSISTENT", "UNKNOWN_ORDER_STATUS"),
                    List.of("orderStatus"), RefundNextAction.CONTACT_SUPPORT);
        }
        if (orderStatus == OrderStatus.PENDING_PAYMENT) {
            return result(order, details, RefundDecision.INELIGIBLE, null,
                    List.of("ORDER_UNPAID"), List.of(), RefundNextAction.CANCEL_ORDER);
        }
        if (orderStatus == OrderStatus.CANCELLED) {
            return result(order, details, RefundDecision.INELIGIBLE, null,
                    List.of("ORDER_CANCELLED"), List.of(), RefundNextAction.NONE);
        }

        RefundReasonType reasonType = resolvedCommand.resolvedReasonType();
        if (reasonType != RefundReasonType.NO_REASON) {
            return evaluateProblemBasedRequest(order, details, resolvedCommand, reasonType);
        }

        if (order.getDeliveryStatus() == null) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, null,
                    List.of("MISSING_DELIVERY_STATUS"), List.of("deliveryStatus"),
                    RefundNextAction.PROVIDE_INFORMATION);
        }
        DeliveryStatus deliveryStatus = DeliveryStatus.fromCode(order.getDeliveryStatus()).orElse(null);
        if (deliveryStatus == null) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, null,
                    List.of("DATA_INCONSISTENT", "UNKNOWN_DELIVERY_STATUS"),
                    List.of("deliveryStatus"), RefundNextAction.CONTACT_SUPPORT);
        }
        if (orderStatus == OrderStatus.PAID && deliveryStatus != DeliveryStatus.NOT_SHIPPED) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, null,
                    List.of("DATA_INCONSISTENT", "PAID_ORDER_HAS_DELIVERY_PROGRESS"),
                    List.of(), RefundNextAction.CONTACT_SUPPORT);
        }
        if (orderStatus == OrderStatus.COMPLETED && deliveryStatus != DeliveryStatus.SIGNED) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, null,
                    List.of("DATA_INCONSISTENT", "COMPLETED_ORDER_NOT_SIGNED"),
                    List.of("signedAt"), RefundNextAction.CONTACT_SUPPORT);
        }

        return switch (deliveryStatus) {
            case NOT_SHIPPED -> evaluateUnshipped(order, details, productType, orderStatus);
            case IN_TRANSIT, REJECTED -> result(order, details, RefundDecision.MANUAL_REVIEW,
                    RefundOperationType.REFUND_ONLY,
                    List.of(deliveryStatus == DeliveryStatus.IN_TRANSIT ? "ORDER_IN_TRANSIT" : "DELIVERY_REJECTED"),
                    List.of(), RefundNextAction.REQUEST_LOGISTICS_INTERCEPT);
            case SIGNED -> evaluateSignedNoReason(order, details, productType, resolvedCommand);
        };
    }

    private RefundEligibilityResult evaluateProblemBasedRequest(Order order,
                                                                List<OrderDetail> details,
                                                                RefundEligibilityCommand command,
                                                                RefundReasonType reasonType) {
        if (reasonType == RefundReasonType.OTHER) {
            return result(order, details, RefundDecision.MANUAL_REVIEW, resolveOperationType(order),
                    List.of("OTHER_REASON_REQUIRES_REVIEW"), List.of(), RefundNextAction.CONTACT_SUPPORT);
        }

        List<String> missingFields = new ArrayList<>();
        if (command.reasonDescription() == null || command.reasonDescription().isBlank()) {
            missingFields.add("reasonDescription");
        }
        if (command.resolvedEvidenceUrls().isEmpty()) {
            missingFields.add("evidenceUrls");
        }
        if (!missingFields.isEmpty()) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, resolveOperationType(order),
                    List.of("PROBLEM_EVIDENCE_REQUIRED"), missingFields,
                    RefundNextAction.PROVIDE_INFORMATION);
        }
        return result(order, details, RefundDecision.ELIGIBLE, resolveOperationType(order),
                List.of("PROBLEM_BASED_AFTER_SALES_SUPPORTED", reasonType.name()), List.of(),
                RefundNextAction.SUBMIT_REFUND_REQUEST);
    }

    private RefundEligibilityResult evaluateUnshipped(Order order,
                                                      List<OrderDetail> details,
                                                      ProductType productType,
                                                      OrderStatus orderStatus) {
        if (orderStatus != OrderStatus.PAID) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, RefundOperationType.REFUND_ONLY,
                    List.of("DATA_INCONSISTENT", "UNSHIPPED_STATUS_MISMATCH"), List.of(),
                    RefundNextAction.CONTACT_SUPPORT);
        }
        if (productType == ProductType.CUSTOMIZED) {
            if (order.getProductionStatus() == null) {
                return result(order, details, RefundDecision.NEED_MORE_INFO, RefundOperationType.REFUND_ONLY,
                        List.of("MISSING_PRODUCTION_STATUS"), List.of("productionStatus"),
                        RefundNextAction.PROVIDE_INFORMATION);
            }
            ProductionStatus productionStatus = ProductionStatus.fromCode(order.getProductionStatus()).orElse(null);
            if (productionStatus == null) {
                return result(order, details, RefundDecision.NEED_MORE_INFO, RefundOperationType.REFUND_ONLY,
                        List.of("DATA_INCONSISTENT", "UNKNOWN_PRODUCTION_STATUS"),
                        List.of("productionStatus"), RefundNextAction.CONTACT_SUPPORT);
            }
            if (productionStatus != ProductionStatus.NOT_STARTED) {
                return result(order, details, RefundDecision.MANUAL_REVIEW, RefundOperationType.REFUND_ONLY,
                        List.of("CUSTOM_PRODUCTION_STARTED"), List.of(), RefundNextAction.CONTACT_SUPPORT);
            }
        }
        if (productType == ProductType.VIRTUAL) {
            if (order.getDigitalDeliveryStatus() == null) {
                return result(order, details, RefundDecision.NEED_MORE_INFO, RefundOperationType.REFUND_ONLY,
                        List.of("MISSING_DIGITAL_DELIVERY_STATUS"), List.of("digitalDeliveryStatus"),
                        RefundNextAction.PROVIDE_INFORMATION);
            }
            DigitalDeliveryStatus digitalStatus = DigitalDeliveryStatus
                    .fromCode(order.getDigitalDeliveryStatus()).orElse(null);
            if (digitalStatus == null) {
                return result(order, details, RefundDecision.NEED_MORE_INFO, RefundOperationType.REFUND_ONLY,
                        List.of("DATA_INCONSISTENT", "UNKNOWN_DIGITAL_DELIVERY_STATUS"),
                        List.of("digitalDeliveryStatus"), RefundNextAction.CONTACT_SUPPORT);
            }
            if (digitalStatus != DigitalDeliveryStatus.NOT_DELIVERED) {
                return result(order, details, RefundDecision.INELIGIBLE, RefundOperationType.REFUND_ONLY,
                        List.of("VIRTUAL_PRODUCT_ALREADY_DELIVERED"), List.of(), RefundNextAction.NONE);
            }
        }
        return result(order, details, RefundDecision.ELIGIBLE, RefundOperationType.REFUND_ONLY,
                List.of("PAID_AND_NOT_SHIPPED"), List.of(), RefundNextAction.SUBMIT_REFUND_REQUEST);
    }

    private RefundEligibilityResult evaluateSignedNoReason(Order order,
                                                           List<OrderDetail> details,
                                                           ProductType productType,
                                                           RefundEligibilityCommand command) {
        if (productType != ProductType.ORDINARY) {
            return result(order, details, RefundDecision.INELIGIBLE, RefundOperationType.RETURN_AND_REFUND,
                    List.of("PRODUCT_TYPE_EXCLUDED_FROM_NO_REASON_RETURN"), List.of(), RefundNextAction.NONE);
        }
        LocalDateTime signedAt = order.getSignedAt();
        if (signedAt == null) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, RefundOperationType.RETURN_AND_REFUND,
                    List.of("DATA_INCONSISTENT", "MISSING_SIGNED_AT"), List.of("signedAt"),
                    RefundNextAction.PROVIDE_INFORMATION);
        }
        ZonedDateTime deadline = signedAt.atZone(BUSINESS_ZONE).plusDays(7);
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(BUSINESS_ZONE);
        if (now.isAfter(deadline)) {
            return result(order, details, RefundDecision.INELIGIBLE, RefundOperationType.RETURN_AND_REFUND,
                    List.of("RETURN_WINDOW_EXPIRED"), List.of(), RefundNextAction.NONE);
        }

        List<String> missingFields = new ArrayList<>();
        if (command.customerUsed() == null) {
            missingFields.add("customerUsed");
        }
        if (command.conditionStatus() == null) {
            missingFields.add("conditionStatus");
        }
        if (!missingFields.isEmpty()) {
            return result(order, details, RefundDecision.NEED_MORE_INFO, RefundOperationType.RETURN_AND_REFUND,
                    List.of("PRODUCT_CONDITION_REQUIRED"), missingFields,
                    RefundNextAction.PROVIDE_INFORMATION);
        }
        if (Boolean.TRUE.equals(command.customerUsed())) {
            return result(order, details, RefundDecision.INELIGIBLE, RefundOperationType.RETURN_AND_REFUND,
                    List.of("PRODUCT_USED"), List.of(), RefundNextAction.NONE);
        }
        if (command.conditionStatus() == ProductConditionStatus.NOT_RESALABLE) {
            return result(order, details, RefundDecision.INELIGIBLE, RefundOperationType.RETURN_AND_REFUND,
                    List.of("PRODUCT_NOT_RESALABLE"), List.of(), RefundNextAction.NONE);
        }
        return result(order, details, RefundDecision.ELIGIBLE, RefundOperationType.RETURN_AND_REFUND,
                List.of("WITHIN_SEVEN_DAYS", "PRODUCT_RESALABLE"), List.of(),
                RefundNextAction.SUBMIT_REFUND_REQUEST);
    }

    private RefundOperationType resolveOperationType(Order order) {
        return DeliveryStatus.fromCode(order.getDeliveryStatus()).orElse(null) == DeliveryStatus.SIGNED
                ? RefundOperationType.RETURN_AND_REFUND
                : RefundOperationType.REFUND_ONLY;
    }

    private RefundEligibilityResult result(Order order,
                                           List<OrderDetail> details,
                                           RefundDecision decision,
                                           RefundOperationType operationType,
                                           List<String> reasonCodes,
                                           List<String> missingFields,
                                           RefundNextAction nextAction) {
        List<RefundItemResult> itemResults = details.stream()
                .map(detail -> new RefundItemResult(detail.getDetailId(), detail.getProductType(),
                        decision, List.copyOf(reasonCodes)))
                .toList();
        return new RefundEligibilityResult(
                order.getOrderId(),
                order.getUserId(),
                decision,
                operationType,
                POLICY_VERSION,
                List.copyOf(reasonCodes),
                List.copyOf(missingFields),
                nextAction,
                decision == RefundDecision.ELIGIBLE || decision == RefundDecision.MANUAL_REVIEW
                        ? order.getTotalAmount() : null,
                OffsetDateTime.now(clock),
                itemResults);
    }

}
