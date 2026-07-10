package com.css.mallorderagent.controller;

import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import com.example.mallordermilvusrag.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * 为前端提供订单列表代理，避免浏览器跨域直接访问 mall-order（8081）。
 */
@RestController
@RequestMapping("/agent/order")
public class OrderProxyController {

    private static final Logger log = LoggerFactory.getLogger(OrderProxyController.class);

    private final MallOrderClient mallOrderClient;

    public OrderProxyController(MallOrderClient mallOrderClient) {
        this.mallOrderClient = mallOrderClient;
    }

    @GetMapping("/orders/{userId}")
    public ApiResponse<List<MallOrderDto>> listOrders(@PathVariable("userId") String userId) {
        try {
            return ApiResponse.success(mallOrderClient.getOrdersByUserId(userId));
        } catch (RestClientException e) {
            log.warn("Order proxy failed for userId={}: {}", userId, e.getMessage());
            return ApiResponse.error(503,
                    "订单服务不可用，请先启动 mall-order（默认端口 8081）。详情：" + e.getMessage());
        }
    }
}
