package com.example.mallordermilvusrag.dto;

/**
 * 向量搜索请求 DTO
 */
public class SearchRequest {

    /**
     * 搜索查询文本
     */
    private String query;

    /**
     * 返回结果数量（默认 5）
     */
    private int topK = 5;

    /**
     * 相似度阈值（可选，0.0 ~ 1.0）
     */
    private Double similarityThreshold;

    public SearchRequest() {
    }

    public SearchRequest(String query) {
        this.query = query;
    }

    public SearchRequest(String query, int topK) {
        this.query = query;
        this.topK = topK;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(Double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
