package com.example.mallordermilvusrag.dto;

import java.util.List;

/**
 * 向量搜索结果 DTO
 */
public class SearchResponse {

    private String query;
    private int totalHits;
    private List<SearchHit> hits;

    public SearchResponse() {
    }

    public SearchResponse(String query, int totalHits, List<SearchHit> hits) {
        this.query = query;
        this.totalHits = totalHits;
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

    /**
     * 单个搜索结果
     */
    public static class SearchHit {
        private String id;
        private String content;
        private double score;
        private DocumentMetadata metadata;

        public SearchHit() {
        }

        public SearchHit(String id, String content, double score, DocumentMetadata metadata) {
            this.id = id;
            this.content = content;
            this.score = score;
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

        public DocumentMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(DocumentMetadata metadata) {
            this.metadata = metadata;
        }
    }
}
