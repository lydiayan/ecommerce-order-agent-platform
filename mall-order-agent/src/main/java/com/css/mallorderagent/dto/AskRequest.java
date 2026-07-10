package com.css.mallorderagent.dto;

/**
 * 订单 Agent 问答请求。
 */
public class AskRequest {

    /** 用户问题 */
    private String query;

    /** 会话 ID（sessionId），用于多轮记忆 */
    private String conversationId;

    /** 用户 ID，对应 Redis key 中的 userId */
    private String userId;

    /** 检索返回条数 */
    private int topK = 5;

    private Double similarityThreshold;
    private String sourceFilter;
    private String departmentFilter;
    private String roleFilter;
    private String versionFilter;
    private Boolean enableRerank;
    private Integer rerankTopN;
    private Integer recallTopK;
    private Double rerankMinScore;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
