package com.css.mallorderagent.tool.client;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import com.css.mallorderagent.security.AuthProperties;
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

    public MallOrderClient(RestClient.Builder restClientBuilder, OrderAgentProperties properties,
                           AuthProperties authProperties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getOrder().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authProperties.getServiceToken())
                .build();
    }

    public MallOrderDto getOrderById(String orderId, String userId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/orders/{orderId}")
                            .queryParam("userId", userId)
                            .build(orderId))
                    .retrieve()
                    .body(MallOrderDto.class);
        } catch (RestClientException e) {
            log.warn("Failed to get order, orderFingerprint={}: {}",
                    TracePrivacy.fingerprint(orderId), e.getMessage());
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
            log.warn("Failed to get orders, userFingerprint={}: {}",
                    TracePrivacy.fingerprint(userId), e.getMessage());
            throw e;
        }
    }

    /**
     * 通过受服务令牌保护的 HTTP 接口取消指定用户拥有的订单。
     *
     * @param orderId 待取消订单编号
     * @param userId 订单所属用户编号
     * @return 下游订单服务是否成功取消订单
     */
    public boolean cancelOrder(String orderId, String userId) {
        try {
            Boolean result = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/orders/{orderId}/cancel")
                            .queryParam("userId", userId)
                            .build(orderId))
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (RestClientException e) {
            log.warn("Failed to cancel order, orderFingerprint={}: {}",
                    TracePrivacy.fingerprint(orderId), e.getMessage());
            throw e;
        }
    }

    /** 调用订单服务的 demo 重置接口，恢复预置订单和售后数据。 */
    public void resetDemoOrders() {
        restClient.post()
                .uri("/internal/demo/reset")
                .retrieve()
                .toBodilessEntity();
    }
}
