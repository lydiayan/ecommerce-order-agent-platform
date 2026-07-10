package com.example.mallordermilvusrag.dto;

import java.util.List;

/**
 * 向量搜索结果 DTO
 */
public class SearchResponse {

    private String query;
    private int totalHits;
    private boolean reranked;
    private String traceId;
    private List<SearchHit> hits;

    public SearchResponse() {
    }

    public SearchResponse(String query, int totalHits, List<SearchHit> hits) {
        this(query, totalHits, false, hits);
    }

    public SearchResponse(String query, int totalHits, boolean reranked, List<SearchHit> hits) {
        this.query = query;
        this.totalHits = totalHits;
        this.reranked = reranked;
        this.hits = hits;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }

    public List<SearchHit> getHits() {
        return hits;
    }

    public void setHits(List<SearchHit> hits) {
        this.hits = hits;
    }

    public boolean isReranked() {
        return reranked;
    }

    public void setReranked(boolean reranked) {
        this.reranked = reranked;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 单个搜索结果
     */
    public static class SearchHit {
        private String id;
        private String content;
        /** 主分数：启用 rerank 时为 rerankScore，否则为 vectorScore */
        private double score;
        private Double vectorScore;
        private Double rerankScore;
        private DocumentMetadata metadata;

        public SearchHit() {
        }

        public SearchHit(String id, String content, double score, DocumentMetadata metadata) {
            this(id, content, score, score, null, metadata);
        }

        public SearchHit(String id, String content, double score,
                         Double vectorScore, Double rerankScore, DocumentMetadata metadata) {
            this.id = id;
            this.content = content;
            this.score = score;
            this.vectorScore = vectorScore;
            this.rerankScore = rerankScore;
            this.metadata = metadata;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public Double getVectorScore() {
            return vectorScore;
        }

        public void setVectorScore(Double vectorScore) {
            this.vectorScore = vectorScore;
        }

        public Double getRerankScore() {
            return rerankScore;
        }

        public void setRerankScore(Double rerankScore) {
            this.rerankScore = rerankScore;
        }

        public DocumentMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(DocumentMetadata metadata) {
            this.metadata = metadata;
        }
    }
}
