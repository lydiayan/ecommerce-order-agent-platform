package com.example.mallorder.service;

import com.example.mallorder.entity.AfterSalesRequest;
import com.example.mallorder.entity.Order;
import com.example.mallorder.mapper.AfterSalesRequestMapper;
import com.example.mallorder.mapper.OrderMapper;
import com.example.mallorder.refund.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {
    private static final Set<String> AFTER_SALES_TYPES = Set.of("退货", "退款", "换货", "修改收货地址");

    private final OrderMapper orderMapper;
    private final AfterSalesRequestMapper afterSalesRequestMapper;
    private final RefundEligibilityService refundEligibilityService;

    public OrderService(OrderMapper orderMapper,
                        AfterSalesRequestMapper afterSalesRequestMapper,
                        RefundEligibilityService refundEligibilityService) {
        this.orderMapper = orderMapper;
        this.afterSalesRequestMapper = afterSalesRequestMapper;
        this.refundEligibilityService = refundEligibilityService;
    }

    /**
     * 查询指定用户拥有的订单并补齐订单明细，避免仅凭订单号越权读取。
     *
     * @param orderId 订单编号
     * @param userId 订单所属用户编号
     * @return 包含明细的订单
     * @throws ResponseStatusException 参数为空或订单不属于该用户时抛出
     */
    public Order getOwnedOrder(String orderId, String userId) {
        Order order = orderMapper.selectOwnedOrder(requireText(orderId, "orderId"), requireText(userId, "userId"));
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        order.setOrderDetails(orderMapper.selectOrderDetailsByOrderId(orderId));
        return order;
    }

    /**
     * 查询用户的全部订单，并逐单补齐商品明细。
     *
     * @param userId 用户编号
     * @return 用户订单列表
     */
    public List<Order> getOrdersByUserId(String userId) {
        List<Order> orders = orderMapper.selectOrdersByUserId(requireText(userId, "userId"));
        for (Order order : orders) {
            order.setOrderDetails(orderMapper.selectOrderDetailsByOrderId(order.getOrderId()));
        }
        return orders;
    }

    /**
     * 在校验订单归属后取消当前状态允许取消的订单。
     *
     * @param orderId 订单编号
     * @param userId 订单所属用户编号
     * @return 取消成功时返回 {@code true}
     * @throws ResponseStatusException 订单不存在、无权访问或状态不允许取消时抛出
     */
    @Transactional
    public boolean cancelOrder(String orderId, String userId) {
        getOwnedOrder(orderId, userId);
        if (orderMapper.cancelOrder(orderId, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "current order status cannot be cancelled");
        }
        return true;
    }

    /**
     * 使用最小参数提交售后申请，兼容 MCP 工具的基础调用契约。
     *
     * @param orderId 订单编号
     * @param userId 订单所属用户编号
     * @param operationType 售后类型
     * @return 已存在或新创建的售后工单
     */
    @Transactional
    public AfterSalesRequest submitAfterSalesRequest(String orderId, String userId, String operationType) {
        return submitAfterSalesRequest(orderId,
                new AfterSalesSubmissionCommand(userId, operationType, null, null, null,
                        null, null, List.of()));
    }

    /**
     * 在校验订单归属后，根据实时订单事实计算退款资格。
     *
     * @param orderId 订单编号
     * @param command 用户身份、退款原因和商品状态声明
     * @return 包含结论、原因编码、缺失字段和下一步动作的资格结果
     */
    public RefundEligibilityResult evaluateRefundEligibility(String orderId, RefundEligibilityCommand command) {
        String userId = requireText(command != null ? command.userId() : null, "userId");
        Order order = getOwnedOrder(orderId, userId);
        return refundEligibilityService.evaluate(order, command);
    }

    /**
     * 校验售后类型、订单归属和退款资格后幂等创建售后工单。
     * 同一订单和售后类型已有活动工单时直接返回原记录；业务不符合时抛出
     * {@link AfterSalesRejectionException}，与基础设施异常区分。
     *
     * @param orderId 订单编号
     * @param command 完整售后申请信息
     * @return 已存在或新创建的活动售后工单
     */
    @Transactional
    public AfterSalesRequest submitAfterSalesRequest(String orderId, AfterSalesSubmissionCommand command) {
        String userId = requireText(command != null ? command.userId() : null, "userId");
        String normalizedType = requireText(command.operationType(), "operationType");
        if (!AFTER_SALES_TYPES.contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported operationType");
        }

        Order order = getOwnedOrder(orderId, userId);
        if ("修改收货地址".equals(normalizedType) && order.getOrderStatus() >= 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "shipped order address cannot be changed");
        }
        String activeRequestKey = orderId + ":" + normalizedType;
        AfterSalesRequest existing = afterSalesRequestMapper.selectByActiveRequestKey(activeRequestKey);
        if (existing != null) {
            return existing;
        }

        RefundEligibilityResult eligibility = null;
        if ("退款".equals(normalizedType) || "退货".equals(normalizedType)) {
            eligibility = refundEligibilityService.evaluate(order, command.toEligibilityCommand());
            if (!eligibility.canSubmitRequest()) {
                throw new AfterSalesRejectionException(eligibility);
            }
        } else if (!"修改收货地址".equals(normalizedType)
                && (order.getOrderStatus() == 0 || order.getOrderStatus() == 4)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "current order status does not support after-sales");
        }

        AfterSalesRequest request = new AfterSalesRequest();
        request.setTicketId("SR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        request.setOrderId(orderId);
        request.setUserId(userId);
        request.setOperationType(normalizedType);
        request.setReasonType(command.reasonType() != null ? command.reasonType().name() : RefundReasonType.NO_REASON.name());
        request.setReasonDescription(command.reasonDescription());
        request.setEvidenceUrls(command.evidenceUrls() != null ? List.copyOf(command.evidenceUrls()) : List.of());
        request.setCustomerOpened(command.customerOpened());
        request.setCustomerUsed(command.customerUsed());
        request.setCustomerConditionStatus(command.conditionStatus() != null ? command.conditionStatus().name() : null);
        request.setEligibilityDecision(eligibility != null ? eligibility.decision().name() : null);
        request.setPolicyVersion(eligibility != null ? eligibility.policyVersion() : null);
        request.setActiveRequestKey(activeRequestKey);
        request.setStatus(AfterSalesStatus.PENDING_REVIEW.name());
        afterSalesRequestMapper.insertOrKeepExisting(request);
        return afterSalesRequestMapper.selectByActiveRequestKey(activeRequestKey);
    }

    /**
     * 查询指定用户提交的售后工单。
     *
     * @param userId 用户编号
     * @return 用户售后工单列表
     */
    public List<AfterSalesRequest> getAfterSalesRequests(String userId) {
        return afterSalesRequestMapper.selectByUserId(requireText(userId, "userId"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return value.trim();
    }
}
