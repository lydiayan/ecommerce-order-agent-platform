package com.example.mallordermilvusrag.util;

/**
 * PDF 解析文本清洗：去除排版噪声，并为中文制度类文档插入分段标记，便于后续 token 切分。
 */
public final class PdfTextCleaner {

    private PdfTextCleaner() {
    }

    public static String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = text.replace('\r', '\n')
                .replace('\t', ' ')
                .replace('\u00a0', ' ');

        // 行内 trim 后合并，去掉 PDF 逐行排版产生的大量空白
        cleaned = cleaned.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((left, right) -> left + " " + right)
                .orElse("");

        // 连续空白压缩为一个空格
        cleaned = cleaned.replaceAll("[ \\u3000]+", " ");

        // 去掉汉字之间的多余空格（PDF 对齐残留）
        cleaned = cleaned.replaceAll("(?<=[\\u4e00-\\u9fff\\u3400-\\u4dbf]) +(?=[\\u4e00-\\u9fff\\u3400-\\u4dbf])", "");

        // 章标题前双换行，条标题前单换行（便于结构化切分）
        cleaned = cleaned.replaceAll("(第[0-9一二三四五六七八九十百千]+章)", "\n\n$1");
        cleaned = cleaned.replaceAll("(第[0-9一二三四五六七八九十百千]+条)", "\n$1");

        // 中文句末标点后换行，供 TokenTextSplitter 在 minChunkSizeChars 处找断点
        cleaned = cleaned.replaceAll("([。！？；])", "$1\n");

        // 保留章之间的空行，其余多余空行压缩
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n").trim();

        return cleaned;
    }
}
