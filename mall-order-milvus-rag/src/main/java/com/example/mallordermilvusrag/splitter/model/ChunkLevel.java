package com.example.mallordermilvusrag.splitter.model;

/**
 * Chunk 在父子切分结果中的层级。非父子策略统一使用 {@link #STANDALONE}。
 */
public enum ChunkLevel {
    STANDALONE,
    PARENT,
    CHILD
}
