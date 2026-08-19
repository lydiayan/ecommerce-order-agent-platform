package com.example.mallordercmpserver.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
public class Order implements Serializable {
    private String orderId;
    private String userId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
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
    private Integer deliveryStatus;
    private LocalDateTime shippedAt;
    private LocalDateTime signedAt;
    private Integer productionStatus;
    private LocalDateTime productionStartedAt;
    private Integer digitalDeliveryStatus;
    private LocalDateTime digitalDeliveredAt;
    private LocalDateTime redeemedAt;
    private String paymentMethod;
    private String shippingAddress;
    private String contactPhone;
    private List<OrderDetail> orderDetails;
}
