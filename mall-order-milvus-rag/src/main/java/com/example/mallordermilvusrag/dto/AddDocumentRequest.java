package com.example.mallordermilvusrag.dto;

import java.util.Map;

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
    private Map<String, Object> metadata;

    public AddDocumentRequest() {
    }

    public AddDocumentRequest(String text) {
        this.text = text;
    }

    public AddDocumentRequest(String text, Map<String, Object> metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
