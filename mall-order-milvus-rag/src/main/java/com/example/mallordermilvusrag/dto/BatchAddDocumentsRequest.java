package com.example.mallordermilvusrag.dto;

import java.util.List;

/**
 * 批量添加文档请求 DTO
 */
public class BatchAddDocumentsRequest {

    private List<AddDocumentRequest> documents;

    public BatchAddDocumentsRequest() {
    }

    public BatchAddDocumentsRequest(List<AddDocumentRequest> documents) {
        this.documents = documents;
    }

    public List<AddDocumentRequest> getDocuments() {
        return documents;
    }

    public void setDocuments(List<AddDocumentRequest> documents) {
        this.documents = documents;
    }
}
