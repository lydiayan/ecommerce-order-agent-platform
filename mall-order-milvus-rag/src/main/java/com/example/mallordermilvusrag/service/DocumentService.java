package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.util.ChapterTextSplitter;
import com.example.mallordermilvusrag.util.PdfTextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档处理服务：PDF 清洗 → 按章预切分 → 章内 token 切分。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final TokenTextSplitter textSplitter;

    public DocumentService(RagDocumentProperties ragDocumentProperties) {
        RagDocumentProperties.ChunkProperties chunk = ragDocumentProperties.getChunk();
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(chunk.getChunkSize())
                .withMinChunkSizeChars(chunk.getMinChunkSizeChars())
                .withMinChunkLengthToEmbed(chunk.getMinChunkLengthToEmbed())
                .withMaxNumChunks(chunk.getMaxNumChunks())
                .withKeepSeparator(chunk.isKeepSeparator())
                .build();
        log.info("TokenTextSplitter configured: chunkSize={}, minChunkSizeChars={}, minChunkLengthToEmbed={}",
                chunk.getChunkSize(), chunk.getMinChunkSizeChars(), chunk.getMinChunkLengthToEmbed());
    }

    public List<Document> splitText(String text, DocumentMetadata metadata) {
        Map<String, Object> metaMap = metadata != null ? metadata.toMap() : Map.of();
        return splitCleanedText(cleanText(text), metaMap);
    }

    public List<Document> splitTexts(List<DocumentInput> texts) {
        List<Document> allChunks = new ArrayList<>();
        for (DocumentInput input : texts) {
            Map<String, Object> metaMap = input.metadata() != null ? input.metadata().toMap() : Map.of();
            allChunks.addAll(splitCleanedText(cleanText(input.text()), metaMap));
        }
        return allChunks;
    }

    public List<Document> parsePdf(byte[] pdfBytes, String filename, DocumentMetadata metadata) {
        String resolvedFilename = resolveFilename(filename, metadata);
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return resolvedFilename;
            }
        };
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> pages = reader.get();

        Map<String, Object> metaMap = metadata != null ? metadata.toMap() : Map.of();
        metaMap = new LinkedHashMap<>(metaMap);
        metaMap.put("file_name", resolvedFilename);

        // 先合并全部页再按章切分，避免页边界打断章节
        StringBuilder fullText = new StringBuilder();
        for (Document page : pages) {
            if (!fullText.isEmpty()) {
                fullText.append('\n');
            }
            fullText.append(cleanText(page.getText()));
        }

        List<Document> chunks = splitCleanedText(fullText.toString(), metaMap);
        log.info("PDF parsed: {} pages, file_name={}, split into {} chunks",
                pages.size(), resolvedFilename, chunks.size());
        return chunks;
    }

    /**
     * 先按「第X章」切段，再对每段做 token 切分，保证 chunk 不跨章。
     */
    private List<Document> splitCleanedText(String cleanedText, Map<String, Object> metaMap) {
        List<String> chapterSections = ChapterTextSplitter.splitByChapter(cleanedText);
        List<Document> chapterDocuments = new ArrayList<>(chapterSections.size());
        for (String section : chapterSections) {
            chapterDocuments.add(new Document(section, new LinkedHashMap<>(metaMap)));
        }
        log.debug("Split into {} chapter section(s) before token chunking", chapterDocuments.size());
        return textSplitter.apply(chapterDocuments);
    }

    private static String resolveFilename(String filename, DocumentMetadata metadata) {
        if (filename != null && !filename.isBlank()) {
            return filename;
        }
        if (metadata != null && metadata.getSource() != null && !metadata.getSource().isBlank()) {
            return metadata.getSource();
        }
        return "unknown.pdf";
    }

    static String cleanText(String text) {
        return PdfTextCleaner.clean(text);
    }

    public record DocumentInput(String text, DocumentMetadata metadata) {
    }
}
