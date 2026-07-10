package com.css.mallorderagent.controller;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderProxyControllerStandaloneTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderAgentProperties properties = new OrderAgentProperties();
        properties.getOrder().setBaseUrl("http://127.0.0.1:8081");
        MallOrderClient client = new MallOrderClient(RestClient.builder(), properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderProxyController(client)).build();
    }

    @Test
    void listOrders_liveMallOrder() throws Exception {
        mockMvc.perform(get("/agent/order/orders/USER1005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].orderId").exists());
    }
}
