package com.example.mallordermilvusrag.service;

import com.example.mallorderobservability.trace.RagTraceService;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.SearchRequest;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.token.JTokkitTokenCounter;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import com.example.mallordermilvusrag.splitter.model.ChunkLevel;
import com.example.mallordermilvusrag.splitter.registry.DocumentSplitterRegistry;
import com.example.mallordermilvusrag.splitter.strategy.ContentTypeDocumentSplitter;
import com.example.mallordermilvusrag.splitter.strategy.FixedSizeDocumentSplitter;
import com.example.mallordermilvusrag.splitter.strategy.ParentChildDocumentSplitter;
import com.example.mallordermilvusrag.splitter.strategy.RecursiveDocumentSplitter;
import com.example.mallordermilvusrag.splitter.strategy.SemanticDocumentSplitter;
import com.example.mallordermilvusrag.splitter.strategy.SlidingWindowDocumentSplitter;
import com.example.mallordermilvusrag.splitter.strategy.StructureAwareDocumentSplitter;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_MILVUS_IT", matches = "true")
class RagMilvusIntegrationTest {

    private static MilvusServiceClient client;
    private static String collectionName;
    private static RagService ragService;

    @BeforeAll
    static void setUp() {
        String host = System.getenv().getOrDefault("MILVUS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("MILVUS_PORT", "29530"));
        collectionName = "mall_rag_splitter_it_" + UUID.randomUUID().toString().replace("-", "");
        client = new MilvusServiceClient(ConnectParam.newBuilder().withHost(host).withPort(port).build());

        RagDocumentProperties properties = new RagDocumentProperties();
        properties.setCollectionName(collectionName);
        RagSplitterProperties splitterProperties = new RagSplitterProperties();
        splitterProperties.getParentChild().setParentTokens(32);
        splitterProperties.getParentChild().setChildTokens(10);
        splitterProperties.getParentChild().setChildOverlapTokens(2);
        splitterProperties.afterPropertiesSet();

        EmbeddingModel embeddingModel = new HashEmbeddingModel(1536);
        TokenCounter counter = new JTokkitTokenCounter();
        RecursiveDocumentSplitter recursive = new RecursiveDocumentSplitter(splitterProperties, counter);
        StructureAwareDocumentSplitter structure = new StructureAwareDocumentSplitter(
                splitterProperties, counter, recursive);
        ContentTypeDocumentSplitter content = new ContentTypeDocumentSplitter(
                splitterProperties, counter, recursive, structure);
        ParentChildDocumentSplitter parentChild = new ParentChildDocumentSplitter(
                splitterProperties, counter, content);
        DocumentSplitterRegistry registry = new DocumentSplitterRegistry(List.of(
                new FixedSizeDocumentSplitter(splitterProperties, counter),
                new SlidingWindowDocumentSplitter(splitterProperties, counter),
                recursive,
                structure,
                new SemanticDocumentSplitter(splitterProperties, counter, embeddingModel, recursive),
                parentChild,
                content), content, splitterProperties);
        DocumentService documentService = new DocumentService(registry);

        DashScopeRerankService rerank = Mockito.mock(DashScopeRerankService.class);
        RagTraceService traceService = Mockito.mock(RagTraceService.class);
        Mockito.when(traceService.isEnabled()).thenReturn(false);
        ragService = new RagService(client, embeddingModel, documentService, rerank, properties, traceService);
        ragService.ensureCollection();
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        if (client != null && collectionName != null) {
            R<RpcStatus> result = client.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(collectionName).build());
            assertEquals(R.Status.Success.getCode(), result.getStatus(),
                    () -> "drop collection failed with status " + result.getStatus());
            client.close(5);
        }
    }

    @Test
    void upsertsParentChildChunksAndReturnsAggregatedParentContext() {
        String text = "退款商品需要保持完好。退款申请需要订单号。退款审核通过后创建退货单。"
                + "物流发货后生成运单。物流异常时请联系客服。";
        DocumentMetadata metadata = new DocumentMetadata("integration-test", "QA", "tester", "1", "2026-08-12");

        List<String> first = ragService.addDocument(text, metadata, "milvus-it-doc",
                RagSplitStrategy.PARENT_CHILD, RagContentType.PLAIN_TEXT);
        List<String> second = ragService.addDocument(text, metadata, "milvus-it-doc",
                RagSplitStrategy.PARENT_CHILD, RagContentType.PLAIN_TEXT);
        assertEquals(first, second, "deterministic IDs must make repeated imports idempotent");

        ragService.addDocument("退款申请需要订单号。", metadata, "replace-doc",
                RagSplitStrategy.PARENT_CHILD, RagContentType.PLAIN_TEXT);
        List<String> replacement = ragService.addDocument("退款申请。", metadata, "replace-doc",
                RagSplitStrategy.PARENT_CHILD, RagContentType.PLAIN_TEXT);
        client.flush(FlushParam.newBuilder().addCollectionName(collectionName)
                .withSyncFlush(true).withSyncFlushWaitingTimeout(30L).build());
        java.util.Set<String> remaining = new QueryResultsWrapper(client.query(QueryParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr("document_id == \"replace-doc\"")
                .withOutFields(List.of("id"))
                .build()).getData()).getRowRecords().stream()
                .map(row -> row.getFieldValues().get("id").toString())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.copyOf(replacement), remaining,
                "only IDs from the replacement version may remain");

        SearchRequest request = new SearchRequest("退款申请", 5);
        request.setEnableRerank(false);
        SearchResponse response = ragService.search(request);

        assertFalse(response.getHits().isEmpty());
        SearchResponse.SearchHit hit = response.getHits().stream()
                .filter(candidate -> "milvus-it-doc".equals(candidate.getDocumentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("milvus-it-doc was not retrieved: "
                        + response.getHits().stream().map(SearchResponse.SearchHit::getDocumentId).toList()));
        assertEquals(ChunkLevel.PARENT.name(), hit.getChunkLevel());
        assertEquals(hit.getId(), hit.getParentId());
        assertTrue(hit.getContent().contains("退款申请"));
        assertFalse(hit.getMatchedChunks().isEmpty());
        assertTrue(hit.getMatchedChunks().stream().allMatch(chunk -> !chunk.id().equals(hit.getId())));
        assertEquals("milvus-it-doc", hit.getDocumentId());
        assertEquals(RagSplitStrategy.PARENT_CHILD.name(), hit.getStrategy());
    }

    private static final class HashEmbeddingModel implements EmbeddingModel {

        private final int dimensions;

        private HashEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            return vector(document.getText());
        }

        @Override
        public org.springframework.ai.embedding.EmbeddingResponse call(
                org.springframework.ai.embedding.EmbeddingRequest request) {
            List<org.springframework.ai.embedding.Embedding> embeddings = new java.util.ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new org.springframework.ai.embedding.Embedding(
                        vector(request.getInstructions().get(i)), i));
            }
            return new org.springframework.ai.embedding.EmbeddingResponse(embeddings);
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        private float[] vector(String text) {
            float[] vector = new float[dimensions];
            for (int i = 0; i < text.length(); i++) {
                vector[Math.floorMod(text.charAt(i) * 31 + i, dimensions)] += 1.0f;
            }
            double norm = 0;
            for (float value : vector) norm += value * value;
            norm = Math.sqrt(norm);
            if (norm == 0) vector[0] = 1;
            else for (int i = 0; i < vector.length; i++) vector[i] /= (float) norm;
            return vector;
        }
    }
}
