package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.core.AbstractRagDocumentSplitter;
import com.example.mallordermilvusrag.splitter.model.ChunkLevel;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容类型感知切分：根据显式类型或文档特征，在结构、FAQ、表格、代码和普通文本算法间路由。
 *
 * <p>当前配置将它作为默认通用策略；它不替代其他策略，而是复用它们处理相应内容。</p>
 */
@Component
public class ContentTypeDocumentSplitter extends AbstractRagDocumentSplitter {

    private static final Pattern FAQ_START = Pattern.compile("(?im)^(?:Q(?:uestion)?[:：]|问题[:：]|问[:：])");
    private static final Pattern CODE_BOUNDARY = Pattern.compile(
            "(?m)^[ \\t]*(?:public|protected|private|class|interface|enum|record|def|async\\s+def|function|const|let|var|export)\\b");

    private final RecursiveDocumentSplitter recursiveSplitter;
    private final StructureAwareDocumentSplitter structureSplitter;
    private final RagSplitterProperties.RecursiveProperties recursiveProperties;

    public ContentTypeDocumentSplitter(RagSplitterProperties properties, TokenCounter tokenCounter,
                                       RecursiveDocumentSplitter recursiveSplitter,
                                       StructureAwareDocumentSplitter structureSplitter) {
        super(properties, tokenCounter);
        this.recursiveSplitter = recursiveSplitter;
        this.structureSplitter = structureSplitter;
        this.recursiveProperties = properties.getRecursive();
    }

    @Override
    public RagSplitStrategy strategy() {
        return RagSplitStrategy.CONTENT_TYPE_AWARE;
    }

    @Override
    protected List<ChunkDraft> splitDrafts(RagSplitRequest request) {
        SplitOptions options = new SplitOptions(recursiveProperties.getMaxTokens(),
                recursiveProperties.getOverlapTokens(), recursiveProperties.getMinTokens());
        return splitForOptions(request, options);
    }

    List<ChunkDraft> splitForOptions(RagSplitRequest request, SplitOptions options) {
        // PDF 在进入此处前已提取为结构化文本，因此与 Markdown/HTML 共用标题感知切分。
        return switch (request.contentType()) {
            case PDF, MARKDOWN, HTML -> structureSplitter.splitForOptions(request, options);
            case FAQ -> splitFaq(request.text(), options);
            case TABLE -> splitTable(request.text(), options);
            case CODE -> splitCode(request.text(), options);
            case PLAIN_TEXT -> recursiveSplitter.splitRange(request.text(), 0, options);
        };
    }

    public RagContentType detect(RagSplitRequest request) {
        if (request.contentType() != null) {
            return request.contentType();
        }
        // 文件扩展名优先，正文特征兜底；检测顺序用于解决 HTML/代码/表格等特征冲突。
        String source = String.valueOf(request.metadata().getOrDefault("source", "")).toLowerCase(Locale.ROOT);
        String text = request.text().stripLeading();
        if (source.endsWith(".pdf")) return RagContentType.PDF;
        if (source.endsWith(".html") || source.endsWith(".htm") || text.matches("(?s)^<(?:!doctype|html|body|h[1-6]|p|div)\\b.*")) {
            return RagContentType.HTML;
        }
        if (source.matches(".*\\.(?:java|kt|py|js|ts|go|rs|cpp|c|cs)$") || text.contains("```")) {
            return RagContentType.CODE;
        }
        if (FAQ_START.matcher(text).find()) return RagContentType.FAQ;
        if (looksLikeTable(text)) return RagContentType.TABLE;
        if (source.endsWith(".md") || Pattern.compile("(?m)^#{1,6}\\s+").matcher(text).find()) {
            return RagContentType.MARKDOWN;
        }
        return RagContentType.PLAIN_TEXT;
    }

    private List<ChunkDraft> splitFaq(String text, SplitOptions options) {
        List<Integer> starts = new ArrayList<>();
        Matcher matcher = FAQ_START.matcher(text);
        while (matcher.find()) starts.add(matcher.start());
        if (starts.isEmpty()) return recursiveSplitter.splitRange(text, 0, options);

        List<ChunkDraft> result = new ArrayList<>();
        // 每个问题起点到下一个问题起点视为一个完整 Q/A 单元，尽量不拆散问答。
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            addAtomicOrRecursive(result, text.substring(start, end).trim(), start, options);
        }
        return result;
    }

    private List<ChunkDraft> splitTable(String text, SplitOptions options) {
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (lines.size() < 2) return recursiveSplitter.splitRange(text, 0, options);
        String header = lines.get(0);
        List<ChunkDraft> result = new ArrayList<>();
        StringBuilder current = new StringBuilder(header);
        int searchOffset = 0;
        int chunkStart = 0;
        for (int i = 1; i < lines.size(); i++) {
            String candidate = current + "\n" + lines.get(i);
            if (tokenCounter.count(candidate) > options.maxTokens() && current.length() > header.length()) {
                result.add(tableDraft(current.toString(), chunkStart, text.length()));
                // 每个表格块都重复表头，让单独召回的一块仍能解释各列含义。
                int found = text.indexOf(lines.get(i), searchOffset);
                chunkStart = Math.max(0, found);
                current = new StringBuilder(header);
            }
            current.append('\n').append(lines.get(i));
            searchOffset = Math.max(searchOffset, text.indexOf(lines.get(i), searchOffset) + lines.get(i).length());
        }
        if (!current.isEmpty()) {
            result.add(tableDraft(current.toString(), chunkStart, text.length()));
        }
        return result;
    }

    private static ChunkDraft tableDraft(String content, int start, int sourceLength) {
        return new ChunkDraft(content, start, Math.min(sourceLength, start + content.length()),
                "", ChunkLevel.STANDALONE, null,
                java.util.Map.of(RagChunkMetadata.OFFSET_BASIS, "normalized_text"));
    }

    private List<ChunkDraft> splitCode(String text, SplitOptions options) {
        List<ChunkDraft> result = new ArrayList<>();
        // Markdown fenced code block 视为原子单元；超限时才退回递归切分。
        Matcher fence = Pattern.compile("(?s)```.*?```").matcher(text);
        int cursor = 0;
        while (fence.find()) {
            splitCodeRegion(result, text, cursor, fence.start(), options);
            addAtomicOrRecursive(result, fence.group(), fence.start(), options);
            cursor = fence.end();
        }
        splitCodeRegion(result, text, cursor, text.length(), options);
        return result.isEmpty() ? recursiveSplitter.splitRange(text, 0, options) : result;
    }

    private void splitCodeRegion(List<ChunkDraft> result, String text, int start, int end, SplitOptions options) {
        if (end <= start || text.substring(start, end).isBlank()) return;
        String region = text.substring(start, end);
        Matcher matcher = CODE_BOUNDARY.matcher(region);
        List<Integer> boundaries = new ArrayList<>();
        while (matcher.find()) boundaries.add(matcher.start());
        if (boundaries.isEmpty()) {
            addAtomicOrRecursive(result, region.trim(), start, options);
            return;
        }
        if (boundaries.get(0) > 0) boundaries.add(0, 0);
        for (int i = 0; i < boundaries.size(); i++) {
            int localStart = boundaries.get(i);
            int localEnd = i + 1 < boundaries.size() ? boundaries.get(i + 1) : region.length();
            addAtomicOrRecursive(result, region.substring(localStart, localEnd).trim(), start + localStart, options);
        }
    }

    private void addAtomicOrRecursive(List<ChunkDraft> result, String content, int offset, SplitOptions options) {
        if (content.isBlank()) return;
        if (tokenCounter.count(content) <= options.maxTokens()) {
            result.add(new ChunkDraft(content, offset, offset + content.length()));
        } else {
            result.addAll(recursiveSplitter.splitRange(content, offset, options));
        }
    }

    private static boolean looksLikeTable(String text) {
        long pipeRows = text.lines().filter(line -> line.indexOf('|') >= 0).count();
        long tabRows = text.lines().filter(line -> line.indexOf('\t') >= 0).count();
        return pipeRows >= 2 || tabRows >= 2;
    }
}
