package com.example.mallordermilvusrag.dto;

import java.util.List;

public class DocumentImportResult {

    private String filename;
    private DocumentMetadata metadata;
    private int chunkCount;
    private List<String> chunkIds;

    public DocumentImportResult() {
    }

    public DocumentImportResult(String filename, DocumentMetadata metadata,
                                int chunkCount, List<String> chunkIds) {
        this.filename = filename;
        this.metadata = metadata;
        this.chunkCount = chunkCount;
        this.chunkIds = chunkIds;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public DocumentMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(DocumentMetadata metadata) {
        this.metadata = metadata;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public List<String> getChunkIds() {
        return chunkIds;
    }

    public void setChunkIds(List<String> chunkIds) {
        this.chunkIds = chunkIds;
    }
}
