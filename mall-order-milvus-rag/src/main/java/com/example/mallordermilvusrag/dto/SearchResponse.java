package com.example.mallordermilvusrag.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 向量搜索结果 DTO
 */
public class SearchResponse {

    private String query;
    private int totalHits;
    private boolean reranked;
    private String traceId;
    private List<SearchHit> hits;

    public SearchResponse() {
    }

    public SearchResponse(String query, int totalHits, List<SearchHit> hits) {
        this(query, totalHits, false, hits);
    }

    public SearchResponse(String query, int totalHits, boolean reranked, List<SearchHit> hits) {
        this.query = query;
        this.totalHits = totalHits;
        this.reranked = reranked;
        this.hits = hits;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }

    public List<SearchHit> getHits() {
        return hits;
    }

    public void setHits(List<SearchHit> hits) {
        this.hits = hits;
    }

    public boolean isReranked() {
        return reranked;
    }

    public void setReranked(boolean reranked) {
        this.reranked = reranked;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 单个搜索结果
     */
    public static class SearchHit {
        private String id;
        private String content;
        /** 主分数：启用 rerank 时为 rerankScore，否则为 vectorScore */
        private double score;
        private Double vectorScore;
        private Double rerankScore;
        private DocumentMetadata metadata;
        private String documentId;
        private String parentId;
        private String chunkLevel;
        private int chunkIndex;
        private int totalChunks;
        private String strategy;
        private String contentType;
        private String titlePath;
        private long startOffset;
        private long endOffset;
        private List<MatchedChunk> matchedChunks = List.of();

        public SearchHit() {
        }

        public SearchHit(String id, String content, double score, DocumentMetadata metadata) {
            this(id, content, score, score, null, metadata);
        }

        public SearchHit(String id, String content, double score,
                         Double vectorScore, Double rerankScore, DocumentMetadata metadata) {
            this.id = id;
            this.content = content;
            this.score = score;
            this.vectorScore = vectorScore;
            this.rerankScore = rerankScore;
            this.metadata = metadata;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public Double getVectorScore() {
            return vectorScore;
        }

        public void setVectorScore(Double vectorScore) {
            this.vectorScore = vectorScore;
        }

        public Double getRerankScore() {
            return rerankScore;
        }

        public void setRerankScore(Double rerankScore) {
            this.rerankScore = rerankScore;
        }

        public DocumentMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(DocumentMetadata metadata) {
            this.metadata = metadata;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getParentId() {
            return parentId;
        }

        public void setParentId(String parentId) {
            this.parentId = parentId;
        }

        public String getChunkLevel() {
            return chunkLevel;
        }

        public void setChunkLevel(String chunkLevel) {
            this.chunkLevel = chunkLevel;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public void setTotalChunks(int totalChunks) {
            this.totalChunks = totalChunks;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getTitlePath() {
            return titlePath;
        }

        public void setTitlePath(String titlePath) {
            this.titlePath = titlePath;
        }

        public long getStartOffset() {
            return startOffset;
        }

        public void setStartOffset(long startOffset) {
            this.startOffset = startOffset;
        }

        public long getEndOffset() {
            return endOffset;
        }

        public void setEndOffset(long endOffset) {
            this.endOffset = endOffset;
        }

        public List<MatchedChunk> getMatchedChunks() {
            return matchedChunks;
        }

        public void setMatchedChunks(List<MatchedChunk> matchedChunks) {
            this.matchedChunks = matchedChunks == null ? List.of() : List.copyOf(matchedChunks);
        }
    }

    /**
     * Keep this DTO non-final so the Graph checkpoint serializer retains element type
     * information for {@code List<MatchedChunk>} during state cloning.
     */
    public static class MatchedChunk {
        private final String id;
        private final String content;
        private final double vectorScore;
        private final int chunkIndex;
        private final long startOffset;
        private final long endOffset;

        @JsonCreator
        public MatchedChunk(@JsonProperty("id") String id,
                            @JsonProperty("content") String content,
                            @JsonProperty("vectorScore") double vectorScore,
                            @JsonProperty("chunkIndex") int chunkIndex,
                            @JsonProperty("startOffset") long startOffset,
                            @JsonProperty("endOffset") long endOffset) {
            this.id = id;
            this.content = content;
            this.vectorScore = vectorScore;
            this.chunkIndex = chunkIndex;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public String id() {
            return id;
        }

        public String getId() {
            return id;
        }

        public String content() {
            return content;
        }

        public String getContent() {
            return content;
        }

        public double vectorScore() {
            return vectorScore;
        }

        public double getVectorScore() {
            return vectorScore;
        }

        public int chunkIndex() {
            return chunkIndex;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public long startOffset() {
            return startOffset;
        }

        public long getStartOffset() {
            return startOffset;
        }

        public long endOffset() {
            return endOffset;
        }

        public long getEndOffset() {
            return endOffset;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MatchedChunk that)) {
                return false;
            }
            return Double.compare(vectorScore, that.vectorScore) == 0
                    && chunkIndex == that.chunkIndex
                    && startOffset == that.startOffset
                    && endOffset == that.endOffset
                    && Objects.equals(id, that.id)
                    && Objects.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, content, vectorScore, chunkIndex, startOffset, endOffset);
        }

        @Override
        public String toString() {
            return "MatchedChunk[" +
                    "id=" + id +
                    ", content=" + content +
                    ", vectorScore=" + vectorScore +
                    ", chunkIndex=" + chunkIndex +
                    ", startOffset=" + startOffset +
                    ", endOffset=" + endOffset +
                    ']';
        }
    }
}
