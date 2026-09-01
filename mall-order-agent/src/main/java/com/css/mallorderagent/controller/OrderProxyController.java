package com.css.mallorderagent.controller;

import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.demo.DemoActorContext;
import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import com.example.mallordermilvusrag.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

/**
 * 为前端提供订单列表代理，避免浏览器跨域直接访问 mall-order（8081）。
 */
@RestController
@RequestMapping("/agent/order")
public class OrderProxyController {

    private static final Logger log = LoggerFactory.getLogger(OrderProxyController.class);

    private final MallOrderClient mallOrderClient;
    private final DemoPersonaService identityService;

    public OrderProxyController(MallOrderClient mallOrderClient, DemoPersonaService identityService) {
        this.mallOrderClient = mallOrderClient;
        this.identityService = identityService;
    }

    /**
     * 查询当前业务身份有权查看的订单，并对员工视角的敏感字段进行脱敏。
     *
     * @param principal 当前登录身份，用于解析业务角色、客户范围和能力
     * @return 授权范围内的订单列表；下游订单服务异常时返回对应代理错误
     */
    @GetMapping("/orders")
    public ApiResponse<List<MallOrderDto>> listOrders(
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        DemoActorContext actor = identityService.resolveActor(principal.actorUserId());
        try {
            List<MallOrderDto> orders = actor.authorizedCustomerIds().stream()
                    .flatMap(userId -> mallOrderClient.getOrdersByUserId(userId).stream())
                    .toList();
            if (actor.capabilities().contains(com.css.mallorderagent.demo.DemoCapability.ASSIGNED_ORDER_READ)) {
                orders = orders.stream().map(DemoPersonaService::maskOrderForStaff).toList();
            }
            return ApiResponse.success(orders);
        } catch (RestClientResponseException e) {
            int downstreamStatus = e.getStatusCode().value();
            log.warn("Order service returned an error, status={}", downstreamStatus);
            return ApiResponse.error(502,
                    "订单服务已响应，但处理请求失败（HTTP " + downstreamStatus + "），请查看 mall-order 日志。");
        } catch (ResourceAccessException e) {
            log.warn("Order service is unavailable: {}", e.getMessage());
            return ApiResponse.error(503,
                    "订单服务不可用，请确认 mall-order 已启动并监听 8081 端口。");
        } catch (RestClientException e) {
            log.warn("Order proxy failed: {}", e.getMessage());
            return ApiResponse.error(502, "订单服务调用失败，请查看服务日志。");
        }
    }
}
