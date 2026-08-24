package com.css.mallorderagent.feedback;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class FeedbackSanitizer {

    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)\\b(authorization|cookie|set-cookie|password|passwd|api[-_ ]?key|access[-_ ]?token)"
                    + "\\s*[:=]\\s*[^\\s,;]{4,}");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile(
            "(?<!\\d)\\d{6}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx](?!\\d)");
    private static final Pattern ADDRESS = Pattern.compile(
            "(?m)(收货地址|联系地址|详细地址|地址)\\s*[:：]\\s*[^\\r\\n]{4,120}");

    public String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String sanitized = value.replace("\u0000", "");
        sanitized = BEARER.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = CREDENTIAL.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = EMAIL.matcher(sanitized).replaceAll("[EMAIL]");
        sanitized = PHONE.matcher(sanitized).replaceAll("[PHONE]");
        sanitized = ID_CARD.matcher(sanitized).replaceAll("[ID_CARD]");
        sanitized = ADDRESS.matcher(sanitized).replaceAll("$1：[ADDRESS]");
        return sanitized.substring(0, Math.min(Math.max(0, maxLength), sanitized.length()));
    }
}
