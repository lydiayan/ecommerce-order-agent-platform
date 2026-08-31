package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.model.RagChunk;
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

    /**
     * 使用默认策略切分文本并转换为 Spring AI 文档。
     *
     * @param text 原始文本
     * @param metadata 业务元数据
     * @return 带公共分块元数据的文档列表
     */
    public List<Document> splitText(String text, DocumentMetadata metadata) {
        return splitText(text, metadata, null, null, null);
    }

    /**
     * 按指定文档编号、策略和内容类型切分文本并转换为 Spring AI 文档。
     *
     * @param text 原始文本
     * @param metadata 业务元数据
     * @param documentId 可选稳定文档编号
     * @param strategy 可选切分策略
     * @param contentType 可选内容类型
     * @return 切分后的文档列表
     */
    public List<Document> splitText(String text, DocumentMetadata metadata, String documentId,
                                    RagSplitStrategy strategy, RagContentType contentType) {
        return splitTextChunks(text, metadata, documentId, strategy, contentType).stream()
                .map(RagChunk::toDocument)
                .toList();
    }

    /**
     * 只执行文本切分，不转换或持久化结果。预览和正式导入共用该入口，保证算法一致。
     *
     * @param text 原始文本
     * @param metadata 业务元数据
     * @param documentId 可选稳定文档编号
     * @param strategy 可选切分策略
     * @param contentType 可选内容类型
     * @return 带偏移、Token 和层级信息的分块
     */
    public List<RagChunk> splitTextChunks(String text, DocumentMetadata metadata, String documentId,
                                          RagSplitStrategy strategy, RagContentType contentType) {
        Map<String, Object> metaMap = metadata != null ? metadata.toMap() : Map.of();
        RagSplitRequest request = new RagSplitRequest(text, metaMap, documentId, strategy, contentType);
        return splitterRegistry.split(request);
    }

    /**
     * 依次切分多份文本并合并所有结果。
     *
     * @param texts 文本输入列表
     * @return 合并后的文档分块
     */
    public List<Document> splitTexts(List<DocumentInput> texts) {
        List<Document> allChunks = new ArrayList<>();
        for (DocumentInput input : texts) {
            allChunks.addAll(splitText(input.text(), input.metadata(), input.documentId(),
                    input.strategy(), input.contentType()));
        }
        return allChunks;
    }

    /**
     * 使用默认策略解析并切分 PDF，返回 Spring AI 文档。
     *
     * @param pdfBytes PDF 字节
     * @param filename 文件名
     * @param metadata 业务元数据
     * @return PDF 文档分块
     */
    public List<Document> parsePdf(byte[] pdfBytes, String filename, DocumentMetadata metadata) {
        return parsePdf(pdfBytes, filename, metadata, null, null);
    }

    /**
     * 按指定编号和策略解析、清洗并切分 PDF。
     *
     * @param pdfBytes PDF 字节
     * @param filename 文件名
     * @param metadata 业务元数据
     * @param documentId 可选稳定文档编号
     * @param strategy 可选切分策略
     * @return PDF 文档分块
     */
    public List<Document> parsePdf(byte[] pdfBytes, String filename, DocumentMetadata metadata,
                                   String documentId, RagSplitStrategy strategy) {
        return parsePdfChunks(pdfBytes, filename, metadata, documentId, strategy).stream()
                .map(RagChunk::toDocument)
                .toList();
    }

    /**
     * 按页提取和清洗 PDF 文本后执行切分，不生成 embedding 或写入 Milvus。
     *
     * @param pdfBytes PDF 字节
     * @param filename 文件名
     * @param metadata 业务元数据
     * @param documentId 可选稳定文档编号
     * @param strategy 可选切分策略
     * @return 保留原文偏移和分块元数据的结果
     */
    public List<RagChunk> parsePdfChunks(byte[] pdfBytes, String filename, DocumentMetadata metadata,
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
        List<RagChunk> chunks = splitterRegistry.split(request);
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
