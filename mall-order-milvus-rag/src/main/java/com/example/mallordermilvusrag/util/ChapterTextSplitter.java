package com.example.mallordermilvusrag.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按「第X章」预切分，避免 TokenTextSplitter 跨章节切 chunk。
 */
public final class ChapterTextSplitter {

    private static final Pattern CHAPTER_HEADING =
            Pattern.compile("第[0-9一二三四五六七八九十百千]+章");

    private ChapterTextSplitter() {
    }

    /**
     * 将清洗后的全文按「第X章」拆成多个段落，每段以章节标题开头（首段可无标题）。
     */
    public static List<String> splitByChapter(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Matcher matcher = CHAPTER_HEADING.matcher(text);
        if (!matcher.find()) {
            return List.of(text.trim());
        }

        List<String> sections = new ArrayList<>();
        int chapterStart = matcher.start();

        if (chapterStart > 0) {
            String preamble = text.substring(0, chapterStart).trim();
            if (!preamble.isBlank()) {
                sections.add(preamble);
            }
        }

        while (chapterStart < text.length()) {
            matcher = CHAPTER_HEADING.matcher(text);
            if (!matcher.find(chapterStart)) {
                break;
            }
            int nextStart = findNextChapterStart(text, matcher.end());
            String section = text.substring(matcher.start(), nextStart).trim();
            if (!section.isBlank()) {
                sections.add(section);
            }
            chapterStart = nextStart;
        }

        return sections.isEmpty() ? List.of(text.trim()) : sections;
    }

    private static int findNextChapterStart(String text, int fromIndex) {
        Matcher matcher = CHAPTER_HEADING.matcher(text);
        if (matcher.find(fromIndex)) {
            return matcher.start();
        }
        return text.length();
    }
}
