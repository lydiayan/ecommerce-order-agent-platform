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

    // ── metadata 标量过滤字段 ──

    /** 按文档来源过滤，如 "售后文档" */
    private String sourceFilter;

    /** 按部门过滤，如 "客服" */
    private String departmentFilter;

    /** 按角色过滤，如 "客服" */
    private String roleFilter;

    /** 按版本过滤，如 "1.0" */
    private String versionFilter;

    /** 是否启用 qwen rerank；null 时使用 rag.yml 默认配置 */
    private Boolean enableRerank;

    /** rerank 后返回条数；null 时使用 topK */
    private Integer rerankTopN;

    /** Milvus 召回条数；null 时使用 topK × candidateMultiplier */
    private Integer recallTopK;

    /** rerank 最低分阈值；null 时使用 rag.yml 默认配置 */
    private Double rerankMinScore;

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

    public String getSourceFilter() {
        return sourceFilter;
    }

    public void setSourceFilter(String sourceFilter) {
        this.sourceFilter = sourceFilter;
    }

    public String getDepartmentFilter() {
        return departmentFilter;
    }

    public void setDepartmentFilter(String departmentFilter) {
        this.departmentFilter = departmentFilter;
    }

    public String getRoleFilter() {
        return roleFilter;
    }

    public void setRoleFilter(String roleFilter) {
        this.roleFilter = roleFilter;
    }

    public String getVersionFilter() {
        return versionFilter;
    }

    public void setVersionFilter(String versionFilter) {
        this.versionFilter = versionFilter;
    }

    public Boolean getEnableRerank() {
        return enableRerank;
    }

    public void setEnableRerank(Boolean enableRerank) {
        this.enableRerank = enableRerank;
    }

    public Integer getRerankTopN() {
        return rerankTopN;
    }

    public void setRerankTopN(Integer rerankTopN) {
        this.rerankTopN = rerankTopN;
    }

    public Integer getRecallTopK() {
        return recallTopK;
    }

    public void setRecallTopK(Integer recallTopK) {
        this.recallTopK = recallTopK;
    }

    public Double getRerankMinScore() {
        return rerankMinScore;
    }

    public void setRerankMinScore(Double rerankMinScore) {
        this.rerankMinScore = rerankMinScore;
    }
}
