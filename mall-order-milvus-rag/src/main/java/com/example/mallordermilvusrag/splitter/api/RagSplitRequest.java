package com.example.mallordermilvusrag.splitter.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次文档切分请求。
 *
 * @param text        原始文档正文；空文本不会产生 Chunk
 * @param metadata    需要透传到每个 Chunk 的业务元数据
 * @param documentId  文档稳定 ID；为空时由正文和 source/version 元数据生成
 * @param strategy    指定策略；为空时由注册器采用全局默认策略
 * @param contentType 指定内容类型；为空时由内容类型策略自动识别
 */
public record RagSplitRequest(
        String text,
        Map<String, Object> metadata,
        String documentId,
        RagSplitStrategy strategy,
        RagContentType contentType
) {
    public RagSplitRequest {
        // 防御性复制，确保一次切分过程中用于生成 ID 和 Chunk 的元数据不会被外部修改。
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public RagSplitRequest withStrategy(RagSplitStrategy value) {
        return new RagSplitRequest(text, metadata, documentId, value, contentType);
    }

    public RagSplitRequest withContentType(RagContentType value) {
        return new RagSplitRequest(text, metadata, documentId, strategy, value);
    }
}
