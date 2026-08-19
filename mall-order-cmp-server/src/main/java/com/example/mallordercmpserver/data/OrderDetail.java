package com.example.mallordercmpserver.data;

import lombok.Data;

import java.math.BigDecimal;


@Data
public class OrderDetail {
    private Integer detailId;
    private String orderId;
    private String productId;
    private String productName;
    /** 商品类型：0 普通商品，1 定制商品，2 生鲜类，3 虚拟商品。 */
    private Integer productType;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String specification;
}
