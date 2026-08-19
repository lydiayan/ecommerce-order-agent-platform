package com.example.mallorder.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
public class Order {
    private String orderId;
    private String userId;
    private Date orderTime;
    private BigDecimal totalAmount;
    /**
     * 订单状态
     * 0:待付款
     * 1:已付款
     * 2:已发货
     * 3:已完成
     * 4:已取消
     */
    private Integer orderStatus;
    /** 物流状态：0 未发货，1 运输中，2 已签收，3 已拒收。 */
    private Integer deliveryStatus;
    private LocalDateTime shippedAt;
    private LocalDateTime signedAt;
    /** 定制履约状态：0 未开始，1 生产中，2 已完成；非定制订单为空。 */
    private Integer productionStatus;
    private LocalDateTime productionStartedAt;
    /** 虚拟履约状态：0 未交付，1 已交付，2 已核销；非虚拟订单为空。 */
    private Integer digitalDeliveryStatus;
    private LocalDateTime digitalDeliveredAt;
    private LocalDateTime redeemedAt;
    private String paymentMethod;
    private String shippingAddress;
    private String contactPhone;
    private List<OrderDetail> orderDetails;
}
