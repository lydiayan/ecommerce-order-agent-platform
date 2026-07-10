package com.css.mallorderagent.tool.client;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * mall-order 服务 HTTP 客户端（默认 {@code http://127.0.0.1:8081}）。
 */
@Component
public class MallOrderClient {

    private static final Logger log = LoggerFactory.getLogger(MallOrderClient.class);

    private final RestClient restClient;

    public MallOrderClient(RestClient.Builder restClientBuilder, OrderAgentProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getOrder().getBaseUrl())
                .build();
    }

    public MallOrderDto getOrderById(String orderId) {
        try {
            return restClient.get()
                    .uri("/orders/order/{orderId}", orderId)
                    .retrieve()
                    .body(MallOrderDto.class);
        } catch (RestClientException e) {
            log.warn("Failed to get order by id {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    public List<MallOrderDto> getOrdersByUserId(String userId) {
        try {
            List<MallOrderDto> orders = restClient.get()
                    .uri("/orders/user/{userId}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<MallOrderDto>>() {
                    });
            return orders != null ? orders : List.of();
        } catch (RestClientException e) {
            log.warn("Failed to get orders by userId {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    public boolean cancelOrder(String orderId) {
        try {
            Boolean result = restClient.post()
                    .uri("/orders/cancel/{orderId}", orderId)
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (RestClientException e) {
            log.warn("Failed to cancel order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }
}
