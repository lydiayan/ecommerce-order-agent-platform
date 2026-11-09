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

    /**
     * 检查订单服务是否已经启动并可接收请求。
     *
     * @return 固定的服务健康状态
     */
    @GetMapping("/health")
    public java.util.Map<String, String> health() {
        return java.util.Map.of("status", "ok", "service", "mall-order");
    }

    /**
     * 查询指定用户拥有的单笔订单，避免跨用户读取订单数据。
     *
     * @param orderId 订单唯一编号
     * @param userId 订单所属用户编号
     * @return 同时匹配订单编号和所属用户的订单
     */
    @GetMapping("/{orderId}")
    public Order getOrderById(@PathVariable("orderId") String orderId,
                              @RequestParam("userId") String userId) {
        log.info("Query order, orderIdLength={}, userIdLength={}", orderId.length(), userId.length());
        return orderService.getOwnedOrder(orderId, userId);
    }

    /**
     * 查询指定用户的全部订单。
     *
     * @param userId 订单所属用户编号
     * @return 该用户拥有的订单列表
     */
    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUserId(@PathVariable("userId") String userId) {
        log.info("Query orders, userIdLength={}", userId.length());
        return orderService.getOrdersByUserId(userId);
    }

    /**
     * 取消指定用户拥有的订单，并执行订单状态合法性检查。
     *
     * @param orderId 待取消的订单编号
     * @param userId 订单所属用户编号，用于校验订单归属
     * @return 是否成功取消订单
     */
    @PostMapping("/{orderId}/cancel")
    public boolean cancelOrder(@PathVariable("orderId") String orderId,
                               @RequestParam("userId") String userId) {
        log.info("Cancel order, orderIdLength={}, userIdLength={}", orderId.length(), userId.length());
        return orderService.cancelOrder(orderId, userId);
    }

    /**
     * 为指定订单提交退款、退货或换货申请，并执行售后资格校验。
     *
     * @param orderId 申请售后的订单编号
     * @param command 申请人、操作类型、原因、商品状态、说明和凭证地址
     * @return 已创建的售后申请；不符合资格时由异常处理器返回结构化拒绝
     */
    @PostMapping("/{orderId}/after-sales")
    public AfterSalesRequest submitAfterSales(@PathVariable("orderId") String orderId,
                                              @RequestBody AfterSalesCommand command) {
        log.info("Submit after-sales request, orderIdLength={}, userIdLength={}, type={}",
                orderId.length(), command.userId().length(), command.operationType());
        return orderService.submitAfterSalesRequest(orderId, command.toSubmissionCommand());
    }

    /**
     * 评估指定订单是否符合退款或退货规则，但不创建售后申请。
     *
     * @param orderId 待评估的订单编号
     * @param command 用户、售后类型、原因和商品状态等资格判断事实
     * @return 售后资格决策、原因码、缺失字段和下一步动作
     */
    @PostMapping("/{orderId}/refund-eligibility")
    public RefundEligibilityResult evaluateRefundEligibility(@PathVariable("orderId") String orderId,
                                                              @RequestBody RefundEligibilityCommand command) {
        log.info("Evaluate refund eligibility, orderIdLength={}, userIdLength={}",
                orderId.length(), command.userId() != null ? command.userId().length() : 0);
        return orderService.evaluateRefundEligibility(orderId, command);
    }

    /**
     * 查询指定用户提交的全部售后申请。
     *
     * @param userId 售后申请所属用户编号
     * @return 该用户的退款、退货和换货申请列表
     */
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
