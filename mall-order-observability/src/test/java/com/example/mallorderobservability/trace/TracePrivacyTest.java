package com.example.mallorderobservability.trace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TracePrivacyTest {

    @Test
    void createsStableTruncatedSha256Fingerprint() {
        String first = TracePrivacy.fingerprint("USER1001");

        assertEquals(first, TracePrivacy.fingerprint("USER1001"));
        assertEquals(16, first.length());
        assertFalse(first.contains("USER1001"));
    }

    @Test
    void recursivelyRemovesRawPayloadsAndKeepsMetrics() {
        Map<String, Object> sanitized = TracePrivacy.sanitizeAttributes(Map.of(
                "query", "raw question",
                "queryFingerprint", "abc123",
                "toolCalls", List.of(Map.of(
                        "toolName", "refundOrder",
                        "inputParams", Map.of("orderId", "ORD20260810001"))),
                "outputToken", 42));

        assertFalse(sanitized.containsKey("query"));
        assertEquals("abc123", sanitized.get("queryFingerprint"));
        assertEquals(42, sanitized.get("outputToken"));

        Map<?, ?> toolCall = (Map<?, ?>) ((List<?>) sanitized.get("toolCalls")).get(0);
        assertEquals("refundOrder", toolCall.get("toolName"));
        assertFalse(toolCall.containsKey("inputParams"));
    }

    @Test
    void storesOnlyCompactErrorLabels() {
        assertEquals("ConnectException", TracePrivacy.sanitizeErrorLabel("ConnectException"));
        assertEquals("redacted", TracePrivacy.sanitizeErrorLabel("request failed for USER1001"));
    }
}
