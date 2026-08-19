package com.example.mallordermilvusrag.splitter.token;

/**
 * Token 计数及 Token 窗口边界查找接口。
 *
 * <p>切分算法只依赖这个接口，因此 Tokenizer 可以独立替换。</p>
 */
public interface TokenCounter {

    /** 返回文本在当前 Tokenizer 下的 Token 数。 */
    int count(String text);

    /**
     * 从 {@code startOffset} 开始，寻找不超过 {@code maxTokens} 的最远字符结束位置。
     */
    int findEnd(String text, int startOffset, int maxTokens);

    /**
     * 在指定区间内寻找后缀起点，使后缀尽量接近但不超过 {@code suffixTokens}。
     * 滑动窗口用它计算下一块应从哪里开始。
     */
    int findStartForSuffix(String text, int startOffset, int endOffset, int suffixTokens);
}
