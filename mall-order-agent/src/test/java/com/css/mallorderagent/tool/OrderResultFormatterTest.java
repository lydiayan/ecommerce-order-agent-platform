package com.css.mallorderagent.tool;

import com.css.mallorderagent.tool.dto.MallOrderDetailDto;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderResultFormatterTest {

    @Test
    void formatsAllSupportedProductTypes() {
        MallOrderDto order = new MallOrderDto();
        order.setOrderId("ORD20260810001");
        order.setOrderDetails(List.of(
                detail("普通商品", 0),
                detail("定制商品", 1),
                detail("生鲜商品", 2)));

        String result = OrderResultFormatter.formatOrder(order);

        assertTrue(result.contains("普通商品 x1，单价 -，商品类型：普通商品"));
        assertTrue(result.contains("定制商品 x1，单价 -，商品类型：定制商品"));
        assertTrue(result.contains("生鲜商品 x1，单价 -，商品类型：生鲜类"));
    }

    private static MallOrderDetailDto detail(String productName, int productType) {
        MallOrderDetailDto detail = new MallOrderDetailDto();
        detail.setProductName(productName);
        detail.setProductType(productType);
        detail.setQuantity(1);
        return detail;
    }
}
