package com.css.mallorderagent.controller;

import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import com.css.mallorderagent.demo.DemoActorContext;
import com.css.mallorderagent.demo.DemoCapability;
import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderProxyControllerTest {

    @Mock
    private MallOrderClient mallOrderClient;
    @Mock
    private DemoPersonaService identityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityUserPrincipal principal = new SecurityUserPrincipal(
                1L, "customer.test", "", "USER1005", "测试客户",
                Set.of("CUSTOMER"), List.of("ROLE_CUSTOMER"), true, true, false, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        when(identityService.resolveActor("USER1005")).thenReturn(new DemoActorContext(
                "USER1005", "", List.of(DemoCapability.OWN_ORDER_READ),
                List.of("USER1005"), List.of(), List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderProxyController(mallOrderClient, identityService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listOrders_success_returnsOrders() throws Exception {
        MallOrderDto order = new MallOrderDto();
        order.setOrderId("ORD20250414005");
        order.setUserId("USER1005");
        order.setOrderStatus(3);
        order.setTotalAmount(new BigDecimal("5499.00"));
        when(mallOrderClient.getOrdersByUserId("USER1005")).thenReturn(List.of(order));

        mockMvc.perform(get("/agent/order/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].orderId").value("ORD20250414005"))
                .andExpect(jsonPath("$.data[0].userId").value("USER1005"))
                .andExpect(jsonPath("$.data[0].orderStatus").value(3))
                .andExpect(jsonPath("$.data[0].totalAmount").value(5499.00));
    }

    @Test
    void listOrders_emptyList_returnsSuccessWithEmptyData() throws Exception {
        when(mallOrderClient.getOrdersByUserId("USER1005")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/agent/order/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listOrders_whenOrderServiceUnavailable_returns503Payload() throws Exception {
        when(mallOrderClient.getOrdersByUserId("USER1005"))
                .thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/agent/order/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("订单服务不可用")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void listOrders_whenOrderServiceReturns500_reportsDownstreamFailure() throws Exception {
        when(mallOrderClient.getOrdersByUserId("USER1005"))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8));

        mockMvc.perform(get("/agent/order/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已响应")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("HTTP 500")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
