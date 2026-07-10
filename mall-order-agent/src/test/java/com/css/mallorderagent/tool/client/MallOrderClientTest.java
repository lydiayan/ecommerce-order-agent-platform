package com.css.mallorderagent.tool.client;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MallOrderClientTest {

    @Test
    void getOrdersByUserId_deserializesMallOrderResponse() {
        OrderAgentProperties properties = new OrderAgentProperties();
        properties.getOrder().setBaseUrl("http://localhost:8081");

        MallOrderClient client = new MallOrderClient(RestClient.builder(), properties);
        List<MallOrderDto> orders = client.getOrdersByUserId("USER1005");

        assertFalse(orders.isEmpty());
        assertEquals("USER1005", orders.get(0).getUserId());
    }
}
