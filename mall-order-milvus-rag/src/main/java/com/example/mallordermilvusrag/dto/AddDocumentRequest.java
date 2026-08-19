package com.example.mallordermilvusrag.dto;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;

/**
 * 添加文档请求 DTO
 */
public class AddDocumentRequest {

    /**
     * 文档文本内容
     */
    private String text;

    /**
     * 文档元数据（可选）
     */
    private DocumentMetadata metadata;

    /** Optional stable business identifier. */
    private String documentId;

    /** Optional per-request override; algorithm parameters remain server-managed. */
    private RagSplitStrategy strategy;

    /** Optional explicit type; otherwise inferred from source and content. */
    private RagContentType contentType;

    public AddDocumentRequest() {
    }

    public AddDocumentRequest(String text) {
        this.text = text;
    }

    public AddDocumentRequest(String text, DocumentMetadata metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public DocumentMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(DocumentMetadata metadata) {
        this.metadata = metadata;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public RagSplitStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(RagSplitStrategy strategy) {
        this.strategy = strategy;
    }

    public RagContentType getContentType() {
        return contentType;
    }

    public void setContentType(RagContentType contentType) {
        this.contentType = contentType;
    }
}
