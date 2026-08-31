package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.dto.KnowledgeDocumentRecord;

import java.util.List;
import java.util.Optional;

/** Persistence boundary implemented by the host Agent application. */
public interface KnowledgeDocumentStore {

    List<KnowledgeDocumentRecord> findAll();

    Optional<KnowledgeDocumentRecord> findByFilename(String filename);

    void saveImporting(KnowledgeDocumentRecord record);

    void saveReady(KnowledgeDocumentRecord record);

    void saveFailed(String filename, String errorMessage);
}
