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

/** 在不保存原始业务编号和模型载荷的前提下提供稳定 Trace 关联信息。 */
public final class TracePrivacy {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "query", "userquery", "prompt", "systemprompt", "userprompt",
            "input", "output", "answer", "userid", "orderid", "filter",
            "inputparams", "shippingaddress", "contactphone", "content", "messages");

    private TracePrivacy() {
    }

    /**
     * 对值生成截断的 SHA-256 指纹，用于同值关联而不能还原原文。
     *
     * @param value 原始值
     * @return 16 位十六进制指纹
     */
    public static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * 递归移除业务标识、提示词、回答等敏感字段，同时保留可观测指标。
     *
     * @param attributes 原始属性
     * @return 新建的脱敏属性 Map
     */
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

    /**
     * 只允许紧凑异常标签进入 Trace，任意错误文本会替换为 {@code redacted}。
     *
     * @param value 原始错误标签或文本
     * @return 安全错误标签
     */
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
