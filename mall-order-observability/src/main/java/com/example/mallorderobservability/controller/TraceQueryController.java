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

    @GetMapping("/{traceId}")
    public ResponseEntity<Map<String, Object>> getTrace(@PathVariable String traceId) throws Exception {
        List<Map<String, Object>> events = traceQueryService.getTrace(traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traceId", traceId);
        body.put("eventCount", events.size());
        body.put("events", events);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "mall-order-observability");
    }
}
