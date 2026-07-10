package com.example.mallordermilvusrag.dto;

/**
 * RAG 问答响应：检索结果 + LLM 生成答案。
 */
public class AskResponse {

    private String query;
    private String answer;
    /** 是否基于知识库片段生成（检索到有效参考资料） */
    private boolean grounded;
    private String traceId;
    private SearchResponse retrieval;

    public AskResponse() {
    }

    public AskResponse(String query, String answer, boolean grounded, SearchResponse retrieval) {
        this.query = query;
        this.answer = answer;
        this.grounded = grounded;
        this.retrieval = retrieval;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public SearchResponse getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(SearchResponse retrieval) {
        this.retrieval = retrieval;
    }
}
