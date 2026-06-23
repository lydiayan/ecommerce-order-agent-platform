package com.example.mallordermilvusrag.dto;

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
}
