package com.css.mallorderagent.tool.client;

import com.css.mallorderagent.tool.dto.MallOrderDto;
import com.example.mallordermilvusrag.dto.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MallOrderClientSerializationTest {

    private static final String SAMPLE = """
            [{"orderId":"ORD20250414005","userId":"USER1005","orderTime":"2025-04-14T02:15:56.000+00:00","totalAmount":5499.00,"orderStatus":3,"paymentMethod":"信用卡","shippingAddress":"杭州市西湖区文三路369号","contactPhone":"13800138005","orderDetails":[{"detailId":5,"orderId":"ORD20250414005","productId":"P1004","productName":"OPPO Find X7","productType":0,"quantity":1,"unitPrice":4999.00,"totalPrice":4999.00,"specification":"12GB+256GB 蓝色"}]}]
            """;

    @Test
    void springObjectMapper_roundTripsOrderList() throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        List<MallOrderDto> orders = mapper.readValue(SAMPLE, new TypeReference<>() {
        });
        assertFalse(orders.isEmpty());
        assertEquals(0, orders.get(0).getOrderDetails().get(0).getProductType());
        assertDoesNotThrow(() -> mapper.writeValueAsString(ApiResponse.success(orders)));
    }

}
