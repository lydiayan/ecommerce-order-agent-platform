package com.example.mallorderobservability.service;

import com.example.mallorderobservability.storage.ElasticsearchTraceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "observability.consumer", name = "enabled", havingValue = "true")
public class TraceQueryService {

    private final ElasticsearchTraceRepository repository;

    public TraceQueryService(ElasticsearchTraceRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> getTrace(String traceId) throws IOException {
        return repository.findByTraceId(traceId);
    }
}
