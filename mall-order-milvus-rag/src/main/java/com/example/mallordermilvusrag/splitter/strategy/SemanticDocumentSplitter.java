package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.core.AbstractRagDocumentSplitter;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义切分：计算相邻句子的向量距离，在主题变化明显的位置形成 Chunk 边界。
 *
 * <p>Embedding 不可用或句子过多时自动降级为递归切分，并在 Chunk 元数据中记录降级原因。</p>
 */
@Component
public class SemanticDocumentSplitter extends AbstractRagDocumentSplitter {

    private static final Logger log = LoggerFactory.getLogger(SemanticDocumentSplitter.class);
    private static final Pattern SENTENCE = Pattern.compile("[^。！？!?；;\\n]+[。！？!?；;]?|[^\\n]+(?:\\n|$)");

    private final EmbeddingModel embeddingModel;
    private final RecursiveDocumentSplitter recursiveSplitter;
    private final RagSplitterProperties.SemanticProperties semanticProperties;

    public SemanticDocumentSplitter(RagSplitterProperties properties, TokenCounter tokenCounter,
                                    EmbeddingModel embeddingModel,
                                    RecursiveDocumentSplitter recursiveSplitter) {
        super(properties, tokenCounter);
        this.embeddingModel = embeddingModel;
        this.recursiveSplitter = recursiveSplitter;
        this.semanticProperties = properties.getSemantic();
    }

    @Override
    public RagSplitStrategy strategy() {
        return RagSplitStrategy.SEMANTIC;
    }

    @Override
    protected List<ChunkDraft> splitDrafts(RagSplitRequest request) {
        SplitOptions options = new SplitOptions(semanticProperties.getMaxTokens(), 0,
                semanticProperties.getMinTokens());
        List<Sentence> sentences = sentences(request.text());
        // 少于两句无法计算相邻距离，直接使用不依赖模型的递归策略。
        if (sentences.size() < 2) {
            return recursiveSplitter.splitRange(request.text(), 0, options);
        }
        if (sentences.size() > semanticProperties.getMaxSentences()) {
            return degraded(request.text(), options, "sentence_limit_exceeded");
        }

        try {
            List<float[]> embeddings = embedBatches(sentences);
            double[] distances = adjacentDistances(embeddings);
            // 用距离分位数作为动态阈值，适应不同文档自身的主题变化幅度。
            double threshold = percentile(distances, semanticProperties.getBoundaryPercentile());
            return groupSentences(request.text(), sentences, distances, threshold,
                    semanticProperties.getTargetTokens(), options);
        } catch (RuntimeException exception) {
            log.warn("Semantic split failed; falling back to recursive splitting: {}", exception.getMessage());
            return degraded(request.text(), options, "embedding_failure");
        }
    }

    private List<ChunkDraft> degraded(String text, SplitOptions options, String reason) {
        Map<String, Object> metadata = Map.of(
                RagChunkMetadata.SPLIT_DEGRADED, true,
                RagChunkMetadata.SPLIT_DEGRADED_REASON, reason);
        return recursiveSplitter.splitRange(text, 0, options).stream()
                .map(draft -> new ChunkDraft(draft.content(), draft.startOffset(), draft.endOffset(),
                        draft.titlePath(), draft.level(), draft.groupKey(), metadata))
                .toList();
    }

    private List<float[]> embedBatches(List<Sentence> sentences) {
        List<float[]> result = new ArrayList<>(sentences.size());
        int batchSize = semanticProperties.getBatchSize();
        // 分批调用 Embedding，限制单次请求大小及内存占用。
        for (int start = 0; start < sentences.size(); start += batchSize) {
            int end = Math.min(start + batchSize, sentences.size());
            List<String> batch = sentences.subList(start, end).stream().map(Sentence::content).toList();
            List<float[]> embedded = embeddingModel.embed(batch);
            if (embedded.size() != batch.size()) {
                throw new IllegalStateException("Embedding count does not match sentence count");
            }
            result.addAll(embedded);
        }
        return result;
    }

    private List<ChunkDraft> groupSentences(String text, List<Sentence> sentences, double[] distances,
                                            double threshold, int targetTokens, SplitOptions options) {
        List<ChunkDraft> result = new ArrayList<>();
        int groupStart = 0;
        for (int i = 0; i < sentences.size(); i++) {
            int start = sentences.get(groupStart).start();
            int end = sentences.get(i).end();
            int tokens = tokenCounter.count(text.substring(start, end));
            boolean hardLimit = tokens >= options.maxTokens();
            boolean semanticBoundary = i < distances.length
                    && distances[i] >= threshold
                    && tokens >= targetTokens;
            // 只有达到最小长度后才允许切断，避免语义敏感导致大量过短 Chunk。
            if ((hardLimit || semanticBoundary) && tokens >= options.minTokens()) {
                result.add(new ChunkDraft(text.substring(start, end), start, end));
                groupStart = i + 1;
            }
        }
        if (groupStart < sentences.size()) {
            int start = sentences.get(groupStart).start();
            int end = sentences.get(sentences.size() - 1).end();
            if (!result.isEmpty() && tokenCounter.count(text.substring(start, end)) < options.minTokens()) {
                // 尾块太短时并入前一块，随后由 enforceHardLimit 再检查硬上限。
                ChunkDraft previous = result.remove(result.size() - 1);
                result.add(new ChunkDraft(text.substring(previous.startOffset(), end), previous.startOffset(), end));
            } else {
                result.add(new ChunkDraft(text.substring(start, end), start, end));
            }
        }
        return enforceHardLimit(result, options);
    }

    private List<ChunkDraft> enforceHardLimit(List<ChunkDraft> drafts, SplitOptions options) {
        List<ChunkDraft> result = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            if (tokenCounter.count(draft.content()) <= options.maxTokens()) {
                result.add(draft);
            } else {
                // 语义完整性不能突破模型上下文限制，超限块最终交给递归策略兜底。
                result.addAll(recursiveSplitter.splitRange(draft.content(), draft.startOffset(), options));
            }
        }
        return result;
    }

    static List<Sentence> sentences(String text) {
        List<Sentence> result = new ArrayList<>();
        Matcher matcher = SENTENCE.matcher(text);
        while (matcher.find()) {
            int start = skipLeadingWhitespace(text, matcher.start(), matcher.end());
            int end = skipTrailingWhitespace(text, start, matcher.end());
            if (end > start) {
                result.add(new Sentence(text.substring(start, end), start, end));
            }
        }
        return result;
    }

    static double[] adjacentDistances(List<float[]> embeddings) {
        double[] result = new double[Math.max(0, embeddings.size() - 1)];
        for (int i = 0; i < result.length; i++) {
            // 余弦相似度越低，1-cosine 越大，越可能是主题切换位置。
            result[i] = 1.0 - cosine(embeddings.get(i), embeddings.get(i + 1));
        }
        return result;
    }

    private static double cosine(float[] left, float[] right) {
        if (left.length != right.length || left.length == 0) {
            throw new IllegalArgumentException("Embedding dimensions do not match");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    static double percentile(double[] values, double percentile) {
        if (values.length == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        double normalized = Math.max(0.0, Math.min(1.0, percentile));
        int index = (int) Math.ceil(normalized * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    record Sentence(String content, int start, int end) {
    }
}
