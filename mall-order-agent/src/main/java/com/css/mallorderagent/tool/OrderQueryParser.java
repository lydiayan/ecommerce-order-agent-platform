package com.css.mallorderagent.tool;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从自然语言问题中解析订单号 / 用户 ID。
 */
final class OrderQueryParser {

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(ORD\\d{10,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_ID_PATTERN = Pattern.compile("(USER\\d+)", Pattern.CASE_INSENSITIVE);

    private OrderQueryParser() {
    }

    static Optional<String> parseOrderId(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = ORDER_ID_PATTERN.matcher(query);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toUpperCase());
        }
        return Optional.empty();
    }

    static Optional<String> parseUserIdFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = USER_ID_PATTERN.matcher(query);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toUpperCase());
        }
        return Optional.empty();
    }
}
