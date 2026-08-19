package com.example.mallordermilvusrag.splitter.token;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

/**
 * 基于 JTokkit CL100K_BASE 编码实现 Token 计数。
 */
@Component
public class JTokkitTokenCounter implements TokenCounter {

    private final Encoding encoding = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    @Override
    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : encoding.countTokens(text);
    }

    @Override
    public int findEnd(String text, int startOffset, int maxTokens) {
        if (maxTokens <= 0 || startOffset < 0 || startOffset > text.length()) {
            throw new IllegalArgumentException("Invalid token window");
        }
        int low = startOffset;
        int high = text.length();
        // 用二分定位 Token 预算内的最远字符位置，避免逐字符反复编码整段文本。
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            mid = avoidLowSurrogate(text, mid);
            if (count(text.substring(startOffset, mid)) <= maxTokens) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        // 极端情况下单个 Unicode 码点也可能超过预算，但仍需向前推进，避免切分死循环。
        if (low == startOffset && startOffset < text.length()) {
            return Character.offsetByCodePoints(text, startOffset, 1);
        }
        return low;
    }

    @Override
    public int findStartForSuffix(String text, int startOffset, int endOffset, int suffixTokens) {
        if (suffixTokens <= 0) {
            return endOffset;
        }
        int low = startOffset;
        int high = endOffset;
        // 寻找满足 Token 预算的最靠左位置，即尽可能保留更多重叠上下文。
        while (low < high) {
            int mid = low + (high - low) / 2;
            mid = avoidLowSurrogate(text, mid);
            if (count(text.substring(mid, endOffset)) > suffixTokens) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return Math.max(startOffset, avoidLowSurrogate(text, low));
    }

    private static int avoidLowSurrogate(String text, int offset) {
        // Java offset 基于 UTF-16，不能把 emoji 等补充平面字符从代理对中间切开。
        if (offset > 0 && offset < text.length() && Character.isLowSurrogate(text.charAt(offset))) {
            return offset - 1;
        }
        return offset;
    }
}
