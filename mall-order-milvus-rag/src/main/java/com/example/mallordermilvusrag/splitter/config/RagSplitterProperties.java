package com.example.mallordermilvusrag.splitter.config;

import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 文档切分配置。公共限制放在顶层，每种算法只配置自己真正使用的参数。
 * 结构感知和内容类型感知策略复用 {@link RecursiveProperties}。
 */
@ConfigurationProperties(prefix = "rag.chunk", ignoreUnknownFields = false)
public class  RagSplitterProperties implements InitializingBean {

    /** 导入请求没有指定策略时采用的默认策略。 */
    private RagSplitStrategy strategy = RagSplitStrategy.CONTENT_TYPE_AWARE;
    /** 单篇文档允许生成的最大 Chunk 数，防止异常输入耗尽内存。 */
    private int maxNumChunks = 10000;

    @NestedConfigurationProperty
    private FixedSizeProperties fixedSize = new FixedSizeProperties();
    @NestedConfigurationProperty
    private SlidingWindowProperties slidingWindow = new SlidingWindowProperties();
    @NestedConfigurationProperty
    private RecursiveProperties recursive = new RecursiveProperties();
    @NestedConfigurationProperty
    private SemanticProperties semantic = new SemanticProperties();
    @NestedConfigurationProperty
    private ParentChildProperties parentChild = new ParentChildProperties();

    @Override
    public void afterPropertiesSet() {
        requirePositive(maxNumChunks, "max-num-chunks");
        fixedSize.validate();
        slidingWindow.validate();
        recursive.validate();
        semantic.validate();
        parentChild.validate();
    }

    public RagSplitStrategy getStrategy() { return strategy; }
    public void setStrategy(RagSplitStrategy strategy) {
        this.strategy = strategy != null ? strategy : RagSplitStrategy.CONTENT_TYPE_AWARE;
    }
    public int getMaxNumChunks() { return maxNumChunks; }
    public void setMaxNumChunks(int maxNumChunks) { this.maxNumChunks = maxNumChunks; }
    public FixedSizeProperties getFixedSize() { return fixedSize; }
    public void setFixedSize(FixedSizeProperties value) {
        this.fixedSize = value != null ? value : new FixedSizeProperties();
    }
    public SlidingWindowProperties getSlidingWindow() { return slidingWindow; }
    public void setSlidingWindow(SlidingWindowProperties value) {
        this.slidingWindow = value != null ? value : new SlidingWindowProperties();
    }
    public RecursiveProperties getRecursive() { return recursive; }
    public void setRecursive(RecursiveProperties value) {
        this.recursive = value != null ? value : new RecursiveProperties();
    }
    public SemanticProperties getSemantic() { return semantic; }
    public void setSemantic(SemanticProperties value) {
        this.semantic = value != null ? value : new SemanticProperties();
    }
    public ParentChildProperties getParentChild() { return parentChild; }
    public void setParentChild(ParentChildProperties value) {
        this.parentChild = value != null ? value : new ParentChildProperties();
    }

    public static class FixedSizeProperties {
        private int maxTokens = 250;

        void validate() { requirePositive(maxTokens, "fixed-size.max-tokens"); }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    public static class SlidingWindowProperties {
        private int maxTokens = 250;
        private int overlapTokens = 50;

        void validate() {
            requirePositive(maxTokens, "sliding-window.max-tokens");
            requireOverlap(overlapTokens, maxTokens, "sliding-window.overlap-tokens");
        }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getOverlapTokens() { return overlapTokens; }
        public void setOverlapTokens(int overlapTokens) { this.overlapTokens = overlapTokens; }
    }

    public static class RecursiveProperties {
        private int minTokens = 80;
        private int maxTokens = 400;
        private int overlapTokens = 40;
        private List<String> separators = new ArrayList<>(List.of(
                "\n\n", "\n", "。", "！", "？", "；", ". ", "! ", "? ", "; ", "，", ", ", " "));

        void validate() {
            requirePositive(minTokens, "recursive.min-tokens");
            requirePositive(maxTokens, "recursive.max-tokens");
            if (minTokens > maxTokens) {
                throw invalid("recursive.min-tokens must not exceed recursive.max-tokens");
            }
            requireOverlap(overlapTokens, maxTokens, "recursive.overlap-tokens");
        }
        public int getMinTokens() { return minTokens; }
        public void setMinTokens(int minTokens) { this.minTokens = minTokens; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getOverlapTokens() { return overlapTokens; }
        public void setOverlapTokens(int overlapTokens) { this.overlapTokens = overlapTokens; }
        public List<String> getSeparators() { return List.copyOf(separators); }
        public void setSeparators(List<String> separators) {
            this.separators = separators == null ? new ArrayList<>() : new ArrayList<>(separators);
        }
    }

    public static class SemanticProperties {
        private int minTokens = 100;
        private int targetTokens = 250;
        private int maxTokens = 500;
        private int batchSize = 64;
        private int maxSentences = 2000;
        private double boundaryPercentile = 0.85;

        void validate() {
            requirePositive(minTokens, "semantic.min-tokens");
            requirePositive(targetTokens, "semantic.target-tokens");
            requirePositive(maxTokens, "semantic.max-tokens");
            if (minTokens > targetTokens || targetTokens > maxTokens) {
                throw invalid("semantic token sizes must satisfy min-tokens <= target-tokens <= max-tokens");
            }
            requirePositive(batchSize, "semantic.batch-size");
            requirePositive(maxSentences, "semantic.max-sentences");
            if (!Double.isFinite(boundaryPercentile)
                    || boundaryPercentile < 0.0 || boundaryPercentile > 1.0) {
                throw invalid("semantic.boundary-percentile must be between 0 and 1");
            }
        }
        public int getMinTokens() { return minTokens; }
        public void setMinTokens(int minTokens) { this.minTokens = minTokens; }
        public int getTargetTokens() { return targetTokens; }
        public void setTargetTokens(int targetTokens) { this.targetTokens = targetTokens; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxSentences() { return maxSentences; }
        public void setMaxSentences(int maxSentences) { this.maxSentences = maxSentences; }
        public double getBoundaryPercentile() { return boundaryPercentile; }
        public void setBoundaryPercentile(double value) { this.boundaryPercentile = value; }
    }

    public static class ParentChildProperties {
        private int parentTokens = 800;
        private int childTokens = 200;
        private int childOverlapTokens = 40;

        void validate() {
            requirePositive(parentTokens, "parent-child.parent-tokens");
            requirePositive(childTokens, "parent-child.child-tokens");
            if (childTokens > parentTokens) {
                throw invalid("parent-child.child-tokens must not exceed parent-child.parent-tokens");
            }
            requireOverlap(childOverlapTokens, childTokens, "parent-child.child-overlap-tokens");
        }
        public int getParentTokens() { return parentTokens; }
        public void setParentTokens(int parentTokens) { this.parentTokens = parentTokens; }
        public int getChildTokens() { return childTokens; }
        public void setChildTokens(int childTokens) { this.childTokens = childTokens; }
        public int getChildOverlapTokens() { return childOverlapTokens; }
        public void setChildOverlapTokens(int value) { this.childOverlapTokens = value; }
    }

    private static void requirePositive(int value, String property) {
        if (value <= 0) {
            throw invalid(property + " must be greater than 0");
        }
    }

    private static void requireOverlap(int overlap, int maxTokens, String property) {
        if (overlap < 0 || overlap >= maxTokens) {
            throw invalid(property + " must be >= 0 and less than max-tokens");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid rag.chunk configuration: " + message);
    }
}
