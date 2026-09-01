package com.example.mallordermilvusrag.splitter.core;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.model.ChunkLevel;
import com.example.mallordermilvusrag.splitter.model.RagChunk;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 七种切分策略共享的模板基类。
 *
 * <p>子类只负责把正文切成 {@link ChunkDraft}；本类统一处理请求归一化、稳定 ID、
 * 父子关系解析、Token 统计和标准元数据，避免每种策略生成出不一致的 Chunk。</p>
 */
public abstract class AbstractRagDocumentSplitter {

    private final int maxNumChunks;
    protected final TokenCounter tokenCounter;

    protected AbstractRagDocumentSplitter(RagSplitterProperties properties, TokenCounter tokenCounter) {
        this.maxNumChunks = properties.getMaxNumChunks();
        this.tokenCounter = tokenCounter;
    }

    /** @return 当前实现对应的切分策略 */
    public abstract RagSplitStrategy strategy();

    /**
     * 执行完整切分流水线：校验请求、生成稳定编号、解析父子关系并补齐公共元数据。
     *
     * @param request 已选择策略的切分请求
     * @return 不可变标准分块列表
     */
    public final List<RagChunk> split(RagSplitRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return List.of();
        }
        String text = request.text();
        RagContentType contentType = request.contentType() == null
                ? RagContentType.PLAIN_TEXT : request.contentType();
        String documentId = request.documentId() == null || request.documentId().isBlank()
                ? stableId("doc", identitySource(request), text)
                : request.documentId();
        if (documentId.length() > 64) {
            throw new IllegalArgumentException("documentId must not exceed 64 characters");
        }

        // 具体算法始终拿到完整的策略和内容类型，后续不再处理 null 分支。
        RagSplitRequest normalizedRequest = new RagSplitRequest(text, request.metadata(), documentId,
                request.strategy() == null ? strategy() : request.strategy(), contentType);
        List<ChunkDraft> drafts = new ArrayList<>(splitDrafts(normalizedRequest));
        drafts.removeIf(draft -> draft.content() == null || draft.content().isBlank());
        if (drafts.size() > maxNumChunks) {
            throw new IllegalArgumentException("Document produced " + drafts.size()
                    + " chunks, exceeding max-num-chunks=" + maxNumChunks);
        }

        // 第一遍先生成所有 Chunk ID，并建立父组标识到真实父 Chunk ID 的映射。
        // 这样即使子块紧跟父块输出，也能在第二遍统一解析 parentId。
        Map<Integer, String> chunkIds = new HashMap<>();
        Map<String, String> parentIds = new HashMap<>();
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            String chunkId = stableId("chk", documentId, strategy().name(), draft.level().name(),
                    Integer.toString(i), draft.content());
            chunkIds.put(i, chunkId);
            if (draft.level() == ChunkLevel.PARENT && draft.groupKey() != null) {
                parentIds.put(draft.groupKey(), chunkId);
            }
        }

        // 第二遍把算法内部 Draft 补全为对外 RagChunk，并让算法元数据覆盖同名业务元数据。
        List<RagChunk> chunks = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
            metadata.putAll(draft.metadata());
            chunks.add(new RagChunk(
                    draft.content(), documentId, chunkIds.get(i),
                    draft.level() == ChunkLevel.CHILD ? parentIds.get(draft.groupKey()) : null,
                    draft.level(), i, drafts.size(), strategy(), contentType, draft.titlePath(),
                    Math.max(0, draft.startOffset()), Math.max(draft.startOffset(), draft.endOffset()),
                    tokenCounter.count(draft.content()), metadata));
        }
        return List.copyOf(chunks);
    }

    /**
     * 执行标准切分并转换为 Spring AI 文档。
     *
     * @param request 切分请求
     * @return Spring AI 文档列表
     */
    public final List<Document> apply(RagSplitRequest request) {
        return split(request).stream().map(RagChunk::toDocument).toList();
    }

    /** 子类扩展点：只描述切分边界，不负责生成 ID 和公共元数据。 */
    protected abstract List<ChunkDraft> splitDrafts(RagSplitRequest request);

    /**
     * 普通 Token 窗口。{@code overlapTokens=0} 是固定长度切分，大于 0 时是滑动窗口。
     */
    protected final List<ChunkDraft> windows(String text, int baseOffset, int maxTokens, int overlapTokens) {
        List<ChunkDraft> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = tokenCounter.findEnd(text, start, maxTokens);
            if (end <= start) {
                break;
            }
            result.add(new ChunkDraft(text.substring(start, end), baseOffset + start, baseOffset + end));
            if (end == text.length()) {
                break;
            }
            // 下一块从本块后缀开始，由 overlapTokens 控制重复上下文大小。
            int next = tokenCounter.findStartForSuffix(text, start, end, overlapTokens);
            start = next > start ? next : end;
        }
        return result;
    }

    /**
     * 递归切分的核心：先按 Token 确定硬上限，再按分隔符优先级向左寻找自然边界。
     */
    protected final List<ChunkDraft> recursiveWindows(String text, int baseOffset, int maxTokens,
                                                       int overlapTokens, int minTokens,
                                                       List<String> separators) {
        List<ChunkDraft> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = tokenCounter.findEnd(text, start, maxTokens);
            int end = hardEnd == text.length()
                    ? hardEnd
                    : lastBoundary(text, start, hardEnd, minTokens, separators);
            if (end <= start) {
                end = hardEnd;
            }
            int contentStart = skipLeadingWhitespace(text, start, end);
            int contentEnd = skipTrailingWhitespace(text, contentStart, end);
            if (contentEnd > contentStart) {
                result.add(new ChunkDraft(text.substring(contentStart, contentEnd),
                        baseOffset + contentStart, baseOffset + contentEnd));
            }
            if (end == text.length()) {
                break;
            }
            int next = tokenCounter.findStartForSuffix(text, start, end, overlapTokens);
            start = next > start ? next : end;
        }
        return result;
    }

    protected static int skipLeadingWhitespace(String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return start;
    }

    protected static int skipTrailingWhitespace(String text, int start, int end) {
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    /**
     * 一次策略执行使用的统一参数快照。
     *
     * @param maxTokens     普通切分窗口上限
     * @param overlapTokens 相邻 Chunk 重叠 Token 数
     * @param minTokens     允许形成独立 Chunk 的最小 Token 数
     */
    protected record SplitOptions(int maxTokens, int overlapTokens, int minTokens) {
        public SplitOptions {
        }
    }

    /**
     * 策略执行期间使用的轻量中间结果。
     *
     * <p>{@code groupKey} 只在父子切分中临时关联父块和子块，不会直接写入向量库。</p>
     */
    protected record ChunkDraft(
            String content,
            int startOffset,
            int endOffset,
            String titlePath,
            ChunkLevel level,
            String groupKey,
            Map<String, Object> metadata
    ) {
        public ChunkDraft(String content, int startOffset, int endOffset) {
            this(content, startOffset, endOffset, "", ChunkLevel.STANDALONE, null, Map.of());
        }

        public ChunkDraft {
            titlePath = titlePath == null ? "" : titlePath;
            level = level == null ? ChunkLevel.STANDALONE : level;
            metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
        }

        public ChunkDraft withLevel(ChunkLevel value, String valueGroupKey) {
            return new ChunkDraft(content, startOffset, endOffset, titlePath, value, valueGroupKey, metadata);
        }
    }

    private int lastBoundary(String text, int start, int hardEnd, int minTokens, List<String> separators) {
        int minimumEnd = tokenCounter.findEnd(text, start, Math.max(1, minTokens));
        if (minimumEnd >= hardEnd) {
            return hardEnd;
        }
        // separators 的配置顺序就是优先级，例如先段落、再换行、最后句号或空格。
        for (String separator : separators) {
            int position = text.lastIndexOf(separator, hardEnd - 1);
            if (position >= minimumEnd) {
                return position + separator.length();
            }
        }
        return hardEnd;
    }

    private static String identitySource(RagSplitRequest request) {
        Object source = request.metadata().get("source");
        Object version = request.metadata().get("version");
        return String.valueOf(source == null ? "" : source) + "\u0000"
                + String.valueOf(version == null ? "" : version);
    }

    /**
     * 截取 SHA-256 的前 16 字节生成稳定 ID；同一文档重复导入会得到相同 ID。
     */
    static String stableId(String prefix, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return prefix + "_" + java.util.HexFormat.of().formatHex(digest.digest(), 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
