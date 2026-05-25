package com.example.mallordermilvusrag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档处理服务：文本拆分、清洗等预处理逻辑
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /**
     * 默认文档分割器
     * 将长文本按 token 切分成适合 embedding 和检索的块
     */
    private final TokenTextSplitter textSplitter;

    public DocumentService() {
        // 默认配置：每块最多 500 token，重叠 50 token
        this.textSplitter = new TokenTextSplitter(500, 100, 5, 10000, true);
    }

    /**
     * 将纯文本分割成文档块列表
     *
     * @param text     原始文本
     * @param metadata 元数据（可选）
     * @return 分割后的文档列表
     */
    public List<Document> splitText(String text, Map<String, Object> metadata) {
        Document doc = new Document(text, metadata != null ? metadata : Map.of());
        List<Document> documents = new ArrayList<>();
        documents.add(doc);
        return textSplitter.apply(documents);
    }

    /**
     * 批量将多个文本分割成文档块列表
     *
     * @param texts    文本及元数据列表
     * @return 所有分割后的文档列表
     */
    public List<Document> splitTexts(List<DocumentInput> texts) {
        List<Document> allDocuments = new ArrayList<>();
        for (DocumentInput input : texts) {
            Document doc = new Document(input.text(), input.metadata() != null ? input.metadata() : Map.of());
            allDocuments.add(doc);
        }
        return textSplitter.apply(allDocuments);
    }

    /**
     * 使用 PDF 文档读取器解析 PDF 文件
     *
     * @param pdfBytes PDF 文件的字节数据
     * @return 解析后的文档列表
     */
    public List<Document> parsePdf(byte[] pdfBytes, Map<String, Object> metadata) {
        // 使用 Spring AI 的 PagePdfDocumentReader 需要 Resource
        // 这里提供一个便捷方法通过 InputStreamResource 读取
        org.springframework.core.io.InputStreamResource resource =
                new org.springframework.core.io.InputStreamResource(
                        new java.io.ByteArrayInputStream(pdfBytes));
        org.springframework.ai.reader.pdf.PagePdfDocumentReader reader =
                new org.springframework.ai.reader.pdf.PagePdfDocumentReader(resource);
        List<Document> documents = reader.get();

        // 添加元数据
        if (metadata != null && !metadata.isEmpty()) {
            documents.forEach(doc -> doc.getMetadata().putAll(metadata));
        }

        log.info("PDF parsed: {} pages loaded", documents.size());

        // 对 PDF 内容进行分割
        return textSplitter.apply(documents);
    }

    /**
     * 文档输入对象
     */
    public record DocumentInput(String text, Map<String, Object> metadata) {
    }
}
