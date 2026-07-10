package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 调用 DashScope qwen3-rerank 对召回文档重排序。
 */
@Service
public class DashScopeRerankService {

    private static final Logger log = LoggerFactory.getLogger(DashScopeRerankService.class);

    private final RagDocumentProperties.RerankProperties properties;
    private final RestClient restClient;
    private final String apiKey;

    public DashScopeRerankService(RagDocumentProperties ragDocumentProperties,
                                  @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.properties = ragDocumentProperties.getRerank();
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().build();
    }

    /**
     * @param documents 候选文档正文（与 Milvus 召回顺序一致）
     * @return 按相关性降序的 index + score；失败时返回空列表
     */
    public List<RerankItem> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DASHSCOPE_API_KEY not configured, skip rerank");
            return List.of();
        }

        RerankRequest request = new RerankRequest(
                properties.getModel(),
                query,
                documents,
                topN,
                properties.getInstruct()
        );

        try {
            RerankResponse response = restClient.post()
                    .uri(properties.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(RerankResponse.class);

            if (response == null || response.getResults() == null) {
                log.warn("Empty rerank response");
                return List.of();
            }

            List<RerankItem> items = new ArrayList<>();
            for (RerankResult result : response.getResults()) {
                if (result.getIndex() >= 0 && result.getIndex() < documents.size()) {
                    items.add(new RerankItem(result.getIndex(), result.getRelevanceScore()));
                }
            }
            items.sort(Comparator.comparingDouble(RerankItem::score).reversed());
            log.info("Rerank returned {} result(s) for {} candidate(s)", items.size(), documents.size());
            return items;
        } catch (RestClientException ex) {
            log.error("DashScope rerank failed: {}", ex.getMessage());
            return List.of();
        }
    }

    public record RerankItem(int index, double score) {
    }

    private record RerankRequest(
            String model,
            String query,
            List<String> documents,
            @JsonProperty("top_n") int topN,
            String instruct) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RerankResponse {
        @JsonProperty("results")
        private List<RerankResult> results = List.of();

        public List<RerankResult> getResults() {
            return results;
        }

        public void setResults(List<RerankResult> results) {
            this.results = results;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RerankResult {
        private int index;

        @JsonProperty("relevance_score")
        private double relevanceScore;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }

        public void setRelevanceScore(double relevanceScore) {
            this.relevanceScore = relevanceScore;
        }
    }
}
