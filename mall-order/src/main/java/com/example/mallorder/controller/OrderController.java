package com.example.mallorder.controller;

import com.example.mallorder.entity.Order;
import com.example.mallorder.entity.AfterSalesRequest;
import com.example.mallorder.refund.*;
import com.example.mallorder.service.OrderService;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/orders")
@Log4j2
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/health")
    public java.util.Map<String, String> health() {
        return java.util.Map.of("status", "ok", "service", "mall-order");
    }

    @GetMapping("/{orderId}")
    public Order getOrderById(@PathVariable("orderId") String orderId,
                              @RequestParam("userId") String userId) {
        log.info("Query order, orderIdLength={}, userIdLength={}", orderId.length(), userId.length());
        return orderService.getOwnedOrder(orderId, userId);
    }

    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUserId(@PathVariable("userId") String userId) {
        log.info("Query orders, userIdLength={}", userId.length());
        return orderService.getOrdersByUserId(userId);
    }

    @PostMapping("/{orderId}/cancel")
    public boolean cancelOrder(@PathVariable("orderId") String orderId,
                               @RequestParam("userId") String userId) {
        log.info("Cancel order, orderIdLength={}, userIdLength={}", orderId.length(), userId.length());
        return orderService.cancelOrder(orderId, userId);
    }

    @PostMapping("/{orderId}/after-sales")
    public AfterSalesRequest submitAfterSales(@PathVariable("orderId") String orderId,
                                              @RequestBody AfterSalesCommand command) {
        log.info("Submit after-sales request, orderIdLength={}, userIdLength={}, type={}",
                orderId.length(), command.userId().length(), command.operationType());
        return orderService.submitAfterSalesRequest(orderId, command.toSubmissionCommand());
    }

    @PostMapping("/{orderId}/refund-eligibility")
    public RefundEligibilityResult evaluateRefundEligibility(@PathVariable("orderId") String orderId,
                                                              @RequestBody RefundEligibilityCommand command) {
        log.info("Evaluate refund eligibility, orderIdLength={}, userIdLength={}",
                orderId.length(), command.userId() != null ? command.userId().length() : 0);
        return orderService.evaluateRefundEligibility(orderId, command);
    }

    @GetMapping("/after-sales/user/{userId}")
    public List<AfterSalesRequest> getAfterSalesRequests(@PathVariable("userId") String userId) {
        return orderService.getAfterSalesRequests(userId);
    }

    public record AfterSalesCommand(
            String userId,
            String operationType,
            RefundReasonType reasonType,
            Boolean customerOpened,
            Boolean customerUsed,
            ProductConditionStatus conditionStatus,
            String reasonDescription,
            List<String> evidenceUrls
    ) {
        AfterSalesSubmissionCommand toSubmissionCommand() {
            return new AfterSalesSubmissionCommand(userId, operationType, reasonType,
                    customerOpened, customerUsed, conditionStatus, reasonDescription, evidenceUrls);
        }
    }
}
