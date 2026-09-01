package com.css.mallorderagent.feedback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackSanitizerTest {

    private final FeedbackSanitizer sanitizer = new FeedbackSanitizer();

    @Test
    void sanitize_removesCredentialsAndPersonalData() {
        String raw = "Authorization: Bearer abcdefghijklmnop phone=13812345678 "
                + "email=test@example.com 身份证 110105199001011234\n收货地址：上海市浦东新区测试路 1 号";

        String sanitized = sanitizer.sanitize(raw, 2_000);

        assertFalse(sanitized.contains("abcdefghijklmnop"));
        assertFalse(sanitized.contains("13812345678"));
        assertFalse(sanitized.contains("test@example.com"));
        assertFalse(sanitized.contains("110105199001011234"));
        assertFalse(sanitized.contains("上海市浦东新区"));
        assertTrue(sanitized.contains("[REDACTED]"));
        assertTrue(sanitized.contains("[PHONE]"));
        assertTrue(sanitized.contains("[EMAIL]"));
        assertTrue(sanitized.contains("[ID_CARD]"));
        assertTrue(sanitized.contains("[ADDRESS]"));
    }
}
