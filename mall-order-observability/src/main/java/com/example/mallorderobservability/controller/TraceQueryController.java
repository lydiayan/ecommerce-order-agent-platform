package com.example.mallorderobservability.controller;

import com.example.mallorderobservability.service.TraceQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/observability/traces")
@ConditionalOnProperty(prefix = "observability.consumer", name = "enabled", havingValue = "true")
public class TraceQueryController {

    private final TraceQueryService traceQueryService;

    public TraceQueryController(TraceQueryService traceQueryService) {
        this.traceQueryService = traceQueryService;
    }

    /**
     * 按 Trace ID 查询完整调用链事件，并附带事件总数。
     *
     * @param traceId 待查询的分布式调用链唯一编号
     * @return Trace ID、事件数量和按服务记录的链路事件
     * @throws Exception 底层 Trace 存储查询失败时抛出
     */
    @GetMapping("/{traceId}")
    public ResponseEntity<Map<String, Object>> getTrace(@PathVariable("traceId") String traceId) throws Exception {
        List<Map<String, Object>> events = traceQueryService.getTrace(traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traceId", traceId);
        body.put("eventCount", events.size());
        body.put("events", events);
        return ResponseEntity.ok(body);
    }

    /**
     * 检查可观测查询服务是否已启用并可接收请求。
     *
     * @return 固定的服务健康状态
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "mall-order-observability");
    }
}
