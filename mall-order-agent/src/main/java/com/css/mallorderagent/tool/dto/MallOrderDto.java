package com.css.mallorderagent.tool.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * mall-order 服务订单 DTO（与 /orders API 响应对齐）。
 */
public class MallOrderDto {

    private String orderId;
    private String userId;
    private Date orderTime;
    private BigDecimal totalAmount;
    private Integer orderStatus;
    private String paymentMethod;
    private String shippingAddress;
    private String contactPhone;
    private List<MallOrderDetailDto> orderDetails;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Date getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(Date orderTime) {
        this.orderTime = orderTime;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public List<MallOrderDetailDto> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(List<MallOrderDetailDto> orderDetails) {
        this.orderDetails = orderDetails;
    }
}
