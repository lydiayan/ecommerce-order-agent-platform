package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.core.AbstractRagDocumentSplitter;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构感知切分：识别 Markdown、中国章节和数字编号标题，先划分章节，再在章节内递归切分。
 *
 * <p>每个 Chunk 会保留完整标题路径，例如“退款规则 &gt; 普通商品”，便于检索结果补充上下文。</p>
 */
@Component
public class StructureAwareDocumentSplitter extends AbstractRagDocumentSplitter {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern CHINESE_HEADING = Pattern.compile("^(第[0-9一二三四五六七八九十百千]+[章节条])\\s*(.*)$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^(\\d+(?:\\.\\d+){0,5})[、.\\s]+(.+)$");

    private final RecursiveDocumentSplitter recursiveSplitter;
    private final RagSplitterProperties.RecursiveProperties recursiveProperties;

    public StructureAwareDocumentSplitter(RagSplitterProperties properties, TokenCounter tokenCounter,
                                          RecursiveDocumentSplitter recursiveSplitter) {
        super(properties, tokenCounter);
        this.recursiveSplitter = recursiveSplitter;
        this.recursiveProperties = properties.getRecursive();
    }

    @Override
    public RagSplitStrategy strategy() {
        return RagSplitStrategy.STRUCTURE_AWARE;
    }

    @Override
    protected List<ChunkDraft> splitDrafts(RagSplitRequest request) {
        SplitOptions options = new SplitOptions(recursiveProperties.getMaxTokens(),
                recursiveProperties.getOverlapTokens(), recursiveProperties.getMinTokens());
        return splitForOptions(request, options);
    }

    List<ChunkDraft> splitForOptions(RagSplitRequest request, SplitOptions options) {
        if (request.contentType() != RagContentType.HTML) {
            return splitStructured(request.text(), options);
        }
        // HTML 先转成接近 Markdown 的规范文本。此后 offset 对应规范文本，而不是原始 HTML。
        return splitStructured(normalizeHtml(request.text()), options).stream()
                .map(draft -> new ChunkDraft(draft.content(), draft.startOffset(), draft.endOffset(),
                        draft.titlePath(), draft.level(), draft.groupKey(),
                        Map.of(RagChunkMetadata.OFFSET_BASIS, "normalized_text")))
                .toList();
    }

    List<ChunkDraft> splitStructured(String text, SplitOptions options) {
        List<Section> sections = sections(text);
        List<ChunkDraft> result = new ArrayList<>();
        // 标题负责确定章节语义，章节内部仍由递归策略执行 Token 限制和自然边界切分。
        for (Section section : sections) {
            List<ChunkDraft> drafts = recursiveSplitter.splitRange(section.content(), section.startOffset(), options);
            for (ChunkDraft draft : drafts) {
                result.add(new ChunkDraft(draft.content(), draft.startOffset(), draft.endOffset(),
                        section.titlePath(), draft.level(), draft.groupKey(), draft.metadata()));
            }
        }
        return result;
    }

    private static List<Section> sections(String text) {
        List<Section> result = new ArrayList<>();
        String[] titleLevels = new String[6];
        int sectionStart = 0;
        int cursor = 0;
        String activePath = "";
        StringBuilder section = new StringBuilder();

        for (String lineWithBreak : text.split("(?<=\\n)", -1)) {
            String line = lineWithBreak.stripTrailing();
            Heading heading = heading(line);
            if (heading != null) {
                // 遇到新标题先封存上一节，并清除当前标题下所有更深层级的旧路径。
                addSection(result, section, sectionStart, activePath);
                section.setLength(0);
                sectionStart = cursor;
                Arrays.fill(titleLevels, heading.level(), titleLevels.length, null);
                titleLevels[heading.level() - 1] = heading.title();
                activePath = Arrays.stream(titleLevels).filter(value -> value != null && !value.isBlank())
                        .reduce((left, right) -> left + " > " + right).orElse(heading.title());
            }
            section.append(lineWithBreak);
            cursor += lineWithBreak.length();
        }
        addSection(result, section, sectionStart, activePath);
        return result.isEmpty() ? List.of(new Section(text, 0, "")) : result;
    }

    private static void addSection(List<Section> sections, StringBuilder value, int offset, String titlePath) {
        String content = value.toString().trim();
        if (!content.isBlank()) {
            int local = value.indexOf(content);
            sections.add(new Section(content, offset + Math.max(local, 0), titlePath));
        }
    }

    private static Heading heading(String line) {
        String stripped = line.strip();
        Matcher markdown = MARKDOWN_HEADING.matcher(stripped);
        if (markdown.matches()) {
            return new Heading(markdown.group(1).length(), markdown.group(2).trim());
        }
        Matcher chinese = CHINESE_HEADING.matcher(stripped);
        if (chinese.matches()) {
            int level = chinese.group(1).endsWith("章") ? 1 : chinese.group(1).endsWith("节") ? 2 : 3;
            return new Heading(level, stripped);
        }
        Matcher numbered = NUMBERED_HEADING.matcher(stripped);
        if (numbered.matches() && stripped.length() <= 120) {
            int level = Math.min(6, (int) numbered.group(1).chars().filter(c -> c == '.').count() + 1);
            return new Heading(level, stripped);
        }
        return null;
    }

    private static String normalizeHtml(String html) {
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        StringBuilder text = new StringBuilder();
        // 只抽取携带正文语义的元素；祖先判断用于避免 p/li/table 的内容被重复收集。
        for (Element element : document.select("h1,h2,h3,h4,h5,h6,p,li,table,pre")) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if (tag.matches("h[1-6]")) {
                text.append("#".repeat(Integer.parseInt(tag.substring(1)))).append(' ')
                        .append(element.text()).append("\n\n");
            } else if (tag.equals("table")) {
                for (Element row : element.select("tr")) {
                    text.append(row.select("th,td").eachText().stream()
                            .reduce((left, right) -> left + " | " + right).orElse("")).append('\n');
                }
                text.append('\n');
            } else if (!element.parents().stream().anyMatch(parent ->
                    parent.tagName().equals("table") || parent.tagName().equals("li")
                            || parent.tagName().equals("p") || parent.tagName().equals("pre"))) {
                text.append(element.text()).append("\n\n");
            }
        }
        String normalized = text.toString().trim();
        return normalized.isBlank() ? document.text() : normalized;
    }

    private record Heading(int level, String title) {
    }

    private record Section(String content, int startOffset, String titlePath) {
    }
}
