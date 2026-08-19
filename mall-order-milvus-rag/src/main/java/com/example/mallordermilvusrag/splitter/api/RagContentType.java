package com.example.mallordermilvusrag.splitter.api;

/**
 * 输入文档的内容类型。
 *
 * <p>它与切分策略是两个维度：策略决定“怎么切”，内容类型决定“按什么结构理解”。</p>
 */
public enum RagContentType {
    /** 无明确结构的普通文本。 */
    PLAIN_TEXT,
    /** 从 PDF 提取并清洗后的文本。 */
    PDF,
    /** 带 Markdown 标题或标记的文本。 */
    MARKDOWN,
    /** 原始 HTML 文本，切分前会先做结构化归一。 */
    HTML,
    /** 由连续问答单元组成的文本。 */
    FAQ,
    /** 使用竖线或制表符分列的表格文本。 */
    TABLE,
    /** 源代码或包含 fenced code block 的文本。 */
    CODE
}
