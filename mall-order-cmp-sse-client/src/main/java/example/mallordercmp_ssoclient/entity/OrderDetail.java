package example.mallordercmp_ssoclient.entity;

import lombok.Data;

import java.math.BigDecimal;


@Data
public class OrderDetail {
    private Integer detailId;
    private String orderId;
    private String productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String specification;
}