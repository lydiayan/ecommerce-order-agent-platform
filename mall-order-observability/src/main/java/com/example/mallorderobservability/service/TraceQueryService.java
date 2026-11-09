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

    /**
     * 从 Elasticsearch 读取指定 Trace 的完整事件序列。
     *
     * @param traceId Trace 编号
     * @return 按时间排序的事件
     * @throws IOException Elasticsearch 查询失败时抛出
     */
    public List<Map<String, Object>> getTrace(String traceId) throws IOException {
        return repository.findByTraceId(traceId);
    }
}
