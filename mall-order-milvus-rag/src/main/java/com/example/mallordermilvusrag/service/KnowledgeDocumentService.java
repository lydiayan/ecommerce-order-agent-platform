package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.AddDocumentRequest;
import com.example.mallordermilvusrag.dto.ChunkPreviewResponse;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.KnowledgeChunkView;
import com.example.mallordermilvusrag.dto.KnowledgeDocumentRecord;
import com.example.mallordermilvusrag.dto.KnowledgeDocumentSummary;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.model.RagChunk;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeDocumentService {

    private final RagDocumentProperties documentProperties;
    private final RagSplitterProperties splitterProperties;
    private final DocumentMetadataRegistry metadataRegistry;
    private final DocumentService documentService;
    private final RagService ragService;
    private final TokenCounter tokenCounter;
    private final PdfUploadPolicy pdfUploadPolicy;
    private final KnowledgeDocumentStore documentStore;
    private final KnowledgeChunkMetrics chunkMetrics;

    public KnowledgeDocumentService(RagDocumentProperties documentProperties,
                                    RagSplitterProperties splitterProperties,
                                    DocumentMetadataRegistry metadataRegistry,
                                    DocumentService documentService,
                                    RagService ragService,
                                    TokenCounter tokenCounter,
                                    PdfUploadPolicy pdfUploadPolicy,
                                    KnowledgeDocumentStore documentStore,
                                    KnowledgeChunkMetrics chunkMetrics) {
        this.documentProperties = documentProperties;
        this.splitterProperties = splitterProperties;
        this.metadataRegistry = metadataRegistry;
        this.documentService = documentService;
        this.ragService = ragService;
        this.tokenCounter = tokenCounter;
        this.pdfUploadPolicy = pdfUploadPolicy;
        this.documentStore = documentStore;
        this.chunkMetrics = chunkMetrics;
    }

    /**
     * 合并配置目录、文档状态表和 Milvus 实际分块，生成管理员知识库目录。
     * 即使旧数据缺少状态记录，也会从当前向量分块构造兼容摘要。
     *
     * @return 不重复的知识文档摘要
     */
    public List<KnowledgeDocumentSummary> catalog() {
        Map<String, KnowledgeDocumentRecord> storedRecords = documentStore.findAll().stream()
                .collect(Collectors.toMap(KnowledgeDocumentRecord::filename, record -> record,
                        (first, ignored) -> first, LinkedHashMap::new));
        Map<String, List<SearchResponse.SearchHit>> bySource = ragService.listAllChunks().stream()
                .filter(hit -> hit.getMetadata().getSource() != null)
                .filter(hit -> !hit.getMetadata().getSource().isBlank())
                .collect(Collectors.groupingBy(hit -> hit.getMetadata().getSource(),
                        LinkedHashMap::new, Collectors.toList()));

        List<KnowledgeDocumentSummary> summaries = new ArrayList<>();
        for (RagDocumentProperties.CatalogEntry entry : documentProperties.getCatalog()) {
            String filename = entry.getFilename();
            if (filename == null || filename.isBlank()) {
                continue;
            }
            List<SearchResponse.SearchHit> current = currentDocument(bySource.getOrDefault(filename, List.of()));
            SearchResponse.SearchHit first = current.stream().findFirst().orElse(null);
            KnowledgeDocumentRecord stored = storedRecords.remove(filename);
            summaries.add(stored != null ? toSummary(stored) : fallbackSummary(
                    filename, metadataRegistry.resolve(filename), current, first));
            bySource.remove(filename);
        }
        for (KnowledgeDocumentRecord stored : storedRecords.values().stream()
                .sorted(Comparator.comparing(KnowledgeDocumentRecord::filename)).toList()) {
            summaries.add(toSummary(stored));
            bySource.remove(stored.filename());
        }
        for (Map.Entry<String, List<SearchResponse.SearchHit>> entry : bySource.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            List<SearchResponse.SearchHit> current = currentDocument(entry.getValue());
            if (current.isEmpty()) {
                continue;
            }
            SearchResponse.SearchHit first = current.get(0);
            summaries.add(fallbackSummary(entry.getKey(), first.getMetadata(), current, first));
        }
        return List.copyOf(summaries);
    }

    /**
     * 按来源读取当前版本的已存储分块和统计信息，不执行 embedding、重排或模型调用。
     *
     * @param source 文档来源文件名
     * @return 文档元数据、切分统计和分块预览
     */
    public ChunkPreviewResponse storedDocument(String source) {
        String normalizedSource = requireSource(source);
        List<SearchResponse.SearchHit> hits = currentDocument(ragService.listChunksBySource(normalizedSource));
        KnowledgeDocumentRecord stored = documentStore.findByFilename(normalizedSource).orElse(null);
        if (stored != null && !stored.documentId().isBlank()) {
            List<SearchResponse.SearchHit> matching = hits.stream()
                    .filter(hit -> stored.documentId().equals(hit.getDocumentId())).toList();
            if (!matching.isEmpty()) hits = matching;
        }
        DocumentMetadata metadata = stored != null ? stored.metadata() : hits.isEmpty()
                ? metadataRegistry.resolve(normalizedSource) : hits.get(0).getMetadata();
        List<KnowledgeChunkView> chunks = hits.stream().map(this::toView).toList();
        String documentId = stored != null ? stored.documentId()
                : hits.isEmpty() ? stableDocumentId(normalizedSource) : hits.get(0).getDocumentId();
        String strategy = stored != null ? stored.strategy()
                : hits.isEmpty() ? splitterProperties.getStrategy().name() : hits.get(0).getStrategy();
        String contentType = stored != null ? stored.contentType()
                : hits.isEmpty() ? RagContentType.PDF.name() : hits.get(0).getContentType();
        return response(normalizedSource, metadata, documentId, strategy, contentType, chunks);
    }

    /**
     * 使用正式切分算法预览文本分块，但不写入向量库或导入状态表。
     *
     * @param request 文本、元数据、文档编号和可选切分策略
     * @return 分块内容及 Token、重叠等统计
     */
    public ChunkPreviewResponse previewText(AddDocumentRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new IllegalArgumentException("Preview text must not be blank");
        }
        DocumentMetadata metadata = request.getMetadata() != null
                ? request.getMetadata() : metadataRegistry.resolve("text-preview.txt");
        String filename = metadata.getSource() == null || metadata.getSource().isBlank()
                ? "text-preview.txt" : metadata.getSource();
        String documentId = request.getDocumentId() == null || request.getDocumentId().isBlank()
                ? stableDocumentId(filename) : request.getDocumentId().trim();
        List<RagChunk> chunks = documentService.splitTextChunks(request.getText(), metadata, documentId,
                request.getStrategy(), request.getContentType());
        return responseFromChunks(filename, metadata, documentId, chunks);
    }

    /**
     * 校验并解析 PDF 后预览正式切分结果，不产生 embedding 或持久化记录。
     *
     * @param file PDF 文件
     * @param departmentOverride 可选部门范围覆盖值
     * @param roleOverride 可选角色范围覆盖值
     * @param versionOverride 可选版本覆盖值
     * @param documentId 可选稳定文档编号
     * @param strategy 可选切分策略
     * @return PDF 分块及统计
     * @throws IOException 读取 PDF 内容失败时抛出
     */
    public ChunkPreviewResponse previewPdf(MultipartFile file,
                                           String departmentOverride,
                                           String roleOverride,
                                           String versionOverride,
                                           String documentId,
                                           RagSplitStrategy strategy) throws IOException {
        String filename = pdfUploadPolicy.validateAndResolveFilename(file);
        DocumentMetadata metadata = metadataRegistry.resolve(
                filename, departmentOverride, roleOverride, versionOverride);
        String resolvedDocumentId = documentId == null || documentId.isBlank()
                ? stableDocumentId(filename) : documentId.trim();
        List<RagChunk> chunks = documentService.parsePdfChunks(
                file.getBytes(), filename, metadata, resolvedDocumentId, strategy);
        return responseFromChunks(filename, metadata, resolvedDocumentId, chunks);
    }

    private ChunkPreviewResponse responseFromChunks(String filename, DocumentMetadata metadata,
                                                    String documentId, List<RagChunk> chunks) {
        List<KnowledgeChunkView> views = chunks.stream().map(this::toView).toList();
        String strategy = chunks.isEmpty()
                ? splitterProperties.getStrategy().name() : chunks.get(0).strategy().name();
        String contentType = chunks.isEmpty()
                ? RagContentType.PDF.name() : chunks.get(0).contentType().name();
        return response(filename, metadata, documentId, strategy, contentType, views);
    }

    private ChunkPreviewResponse response(String filename, DocumentMetadata metadata,
                                          String documentId, String strategy, String contentType,
                                          List<KnowledgeChunkView> chunks) {
        int totalTokens = chunks.stream().mapToInt(KnowledgeChunkView::tokenCount).sum();
        int averageTokens = chunks.isEmpty() ? 0 : Math.round((float) totalTokens / chunks.size());
        int maxTokens = chunks.stream().mapToInt(KnowledgeChunkView::tokenCount).max().orElse(0);
        return new ChunkPreviewResponse(filename, metadata, documentId, strategy, contentType,
                chunks.size(), averageTokens, maxTokens, chunkMetrics.overlapTokens(strategy),
                List.copyOf(chunks));
    }

    private KnowledgeChunkView toView(RagChunk chunk) {
        Map<String, Object> metadata = chunk.metadata();
        return new KnowledgeChunkView(chunk.content(), chunk.documentId(), chunk.chunkId(), chunk.parentId(),
                chunk.chunkLevel().name(), chunk.chunkIndex(), chunk.totalChunks(), chunk.strategy().name(),
                chunk.contentType().name(), chunk.titlePath(), chunk.startOffset(), chunk.endOffset(),
                chunk.tokenCount(), stringValue(metadata.get(RagChunkMetadata.OFFSET_BASIS), "original_text"),
                booleanValue(metadata.get(RagChunkMetadata.SPLIT_DEGRADED)),
                stringValue(metadata.get(RagChunkMetadata.SPLIT_DEGRADED_REASON), ""));
    }

    private KnowledgeChunkView toView(SearchResponse.SearchHit hit) {
        return new KnowledgeChunkView(hit.getContent(), hit.getDocumentId(), hit.getId(), hit.getParentId(),
                hit.getChunkLevel(), hit.getChunkIndex(), hit.getTotalChunks(), hit.getStrategy(),
                hit.getContentType(), hit.getTitlePath(), hit.getStartOffset(), hit.getEndOffset(),
                tokenCounter.count(hit.getContent()), "original_text", false, "");
    }

    private KnowledgeDocumentSummary toSummary(KnowledgeDocumentRecord record) {
        return new KnowledgeDocumentSummary(record.filename(), record.metadata(), record.documentId(),
                record.chunkCount(), record.strategy(), record.contentType(), record.averageTokenCount(),
                record.maxTokenCount(), record.overlapTokenCount(), record.originalFileSize(),
                record.fileSha256(), record.importStatus().name(), record.lastError(),
                record.importedAt(), record.updatedAt());
    }

    private KnowledgeDocumentSummary fallbackSummary(String filename, DocumentMetadata metadata,
                                                     List<SearchResponse.SearchHit> chunks,
                                                     SearchResponse.SearchHit first) {
        String strategy = first != null ? first.getStrategy() : splitterProperties.getStrategy().name();
        return new KnowledgeDocumentSummary(filename, metadata,
                first != null && !first.getDocumentId().isBlank()
                        ? first.getDocumentId() : stableDocumentId(filename),
                chunks.size(), strategy,
                first != null ? first.getContentType() : RagContentType.PDF.name(),
                0, 0, chunkMetrics.overlapTokens(strategy), 0, "",
                chunks.isEmpty() ? "NOT_IMPORTED" : "MILVUS_ONLY", null, null, null);
    }

    private static List<SearchResponse.SearchHit> currentDocument(Collection<SearchResponse.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<String, List<SearchResponse.SearchHit>> byDocument = hits.stream()
                .collect(Collectors.groupingBy(SearchResponse.SearchHit::getDocumentId));
        return byDocument.values().stream()
                .max(Comparator
                        .comparing((List<SearchResponse.SearchHit> group) -> group.stream()
                                .map(hit -> hit.getMetadata().getCreateTime())
                                .filter(Objects::nonNull)
                                .max(String::compareTo)
                                .orElse(""))
                        .thenComparingInt(List::size))
                .orElse(List.of()).stream()
                .sorted(Comparator.comparingInt(SearchResponse.SearchHit::getChunkIndex))
                .toList();
    }

    private static String requireSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Document source must not be blank");
        }
        return source.trim();
    }

    private static String stableDocumentId(String filename) {
        UUID value = UUID.nameUUIDFromBytes(("knowledge:" + filename).getBytes(StandardCharsets.UTF_8));
        return "kb_" + value.toString().replace("-", "");
    }

    private static String stringValue(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(Objects.toString(value, "false"));
    }
}
