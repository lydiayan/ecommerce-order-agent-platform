package com.example.mallordermemory.memory;

/**
 * 轻量 Token 估算（中英文混合场景），用于合并触发判断。
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    /**
     * 估算文本 Token 数：CJK 按 ~1.5 字符/token，其余按 ~4 字符/token。
     */
    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (char ch : text.toCharArray()) {
            if (isCjk(ch)) {
                cjk++;
            } else if (!Character.isWhitespace(ch)) {
                other++;
            }
        }
        return (int) Math.ceil(cjk / 1.5) + (int) Math.ceil(other / 4.0);
    }

    public static int estimate(ShortTermMessage message) {
        return message == null ? 0 : estimate(message.getContent());
    }

    public static int estimate(Iterable<ShortTermMessage> messages) {
        int total = 0;
        if (messages != null) {
            for (ShortTermMessage message : messages) {
                total += estimate(message);
            }
        }
        return total;
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}
