package com.example.mallorderobservability.trace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stable, non-reversible identifiers for trace correlation without storing business IDs. */
public final class TracePrivacy {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "query", "userquery", "prompt", "systemprompt", "userprompt",
            "input", "output", "answer", "userid", "orderid", "filter",
            "inputparams", "shippingaddress", "contactphone", "content", "messages");

    private TracePrivacy() {
    }

    public static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Removes raw business and model payload fields, including nested maps and lists. */
    public static Map<String, Object> sanitizeAttributes(Map<String, ?> attributes) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (attributes == null) {
            return sanitized;
        }
        attributes.forEach((key, value) -> {
            if (key != null && !isSensitiveKey(key)) {
                sanitized.put(key, sanitizeValue(value));
            }
        });
        return sanitized;
    }

    /** Keeps only compact error labels such as ConnectException; arbitrary messages are redacted. */
    public static String sanitizeErrorLabel(String value) {
        if (value == null) {
            return null;
        }
        String label = value.trim();
        return label.matches("[A-Za-z0-9_.$-]{1,128}") ? label : "redacted";
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized);
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                if (key != null && !isSensitiveKey(String.valueOf(key))) {
                    nested.put(String.valueOf(key), sanitizeValue(nestedValue));
                }
            });
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> nested = new ArrayList<>();
            iterable.forEach(item -> nested.add(sanitizeValue(item)));
            return nested;
        }
        return value;
    }
}
