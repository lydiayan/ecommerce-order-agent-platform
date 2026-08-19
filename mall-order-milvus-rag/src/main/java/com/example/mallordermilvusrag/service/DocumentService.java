package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.registry.DocumentSplitterRegistry;
import com.example.mallordermilvusrag.util.PdfTextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentSplitterRegistry splitterRegistry;

    public DocumentService(DocumentSplitterRegistry splitterRegistry) {
        this.splitterRegistry = splitterRegistry;
    }

    public List<Document> splitText(String text, DocumentMetadata metadata) {
        return splitText(text, metadata, null, null, null);
    }

    public List<Document> splitText(String text, DocumentMetadata metadata, String documentId,
                                    RagSplitStrategy strategy, RagContentType contentType) {
        Map<String, Object> metaMap = metadata != null ? metadata.toMap() : Map.of();
        RagSplitRequest request = new RagSplitRequest(text, metaMap, documentId, strategy, contentType);
        return splitterRegistry.split(request).stream().map(chunk -> chunk.toDocument()).toList();
    }

    public List<Document> splitTexts(List<DocumentInput> texts) {
        List<Document> allChunks = new ArrayList<>();
        for (DocumentInput input : texts) {
            allChunks.addAll(splitText(input.text(), input.metadata(), input.documentId(),
                    input.strategy(), input.contentType()));
        }
        return allChunks;
    }

    public List<Document> parsePdf(byte[] pdfBytes, String filename, DocumentMetadata metadata) {
        return parsePdf(pdfBytes, filename, metadata, null, null);
    }

    public List<Document> parsePdf(byte[] pdfBytes, String filename, DocumentMetadata metadata,
                                   String documentId, RagSplitStrategy strategy) {
        String resolvedFilename = resolveFilename(filename, metadata);
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return resolvedFilename;
            }
        };
        List<Document> pages = new PagePdfDocumentReader(resource).get();
        Map<String, Object> metaMap = new LinkedHashMap<>(metadata != null ? metadata.toMap() : Map.of());
        metaMap.put("file_name", resolvedFilename);
        metaMap.putIfAbsent("source", resolvedFilename);

        StringBuilder fullText = new StringBuilder();
        for (Document page : pages) {
            if (!fullText.isEmpty()) fullText.append('\n');
            fullText.append(cleanText(page.getText()));
        }

        RagSplitRequest request = new RagSplitRequest(fullText.toString(), metaMap, documentId,
                strategy, RagContentType.PDF);
        List<Document> chunks = splitterRegistry.split(request).stream().map(chunk -> chunk.toDocument()).toList();
        log.info("PDF parsed: {} pages, file_name={}, split into {} chunks",
                pages.size(), resolvedFilename, chunks.size());
        return chunks;
    }

    private static String resolveFilename(String filename, DocumentMetadata metadata) {
        if (filename != null && !filename.isBlank()) return filename;
        if (metadata != null && metadata.getSource() != null && !metadata.getSource().isBlank()) {
            return metadata.getSource();
        }
        return "unknown.pdf";
    }

    static String cleanText(String text) {
        return PdfTextCleaner.clean(text);
    }

    public record DocumentInput(String text, DocumentMetadata metadata, String documentId,
                                RagSplitStrategy strategy, RagContentType contentType) {
        public DocumentInput(String text, DocumentMetadata metadata) {
            this(text, metadata, null, null, null);
        }
    }
}
