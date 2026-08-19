package com.example.mallordermilvusrag.splitter.api;

/**
 * 系统支持的七种文档切分策略。
 */
public enum RagSplitStrategy {
    /** 严格按 Token 上限连续切分，不保留重叠内容。 */
    FIXED_SIZE,
    /** 按 Token 窗口切分，并把上一块末尾带入下一块。 */
    SLIDING_WINDOW,
    /** 在 Token 上限内，按配置的分隔符优先级递归寻找自然边界。 */
    RECURSIVE,
    /** 先识别标题层级，再在每个章节内递归切分。 */
    STRUCTURE_AWARE,
    /** 根据相邻句向量距离识别主题变化。 */
    SEMANTIC,
    /** 同时生成用于召回的子块和用于返回上下文的父块。 */
    PARENT_CHILD,
    /** 先识别 PDF、FAQ、表格、代码等内容类型，再选择相应切法。 */
    CONTENT_TYPE_AWARE
}
