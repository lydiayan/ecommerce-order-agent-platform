package com.css.mallorderagent.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(AuthProperties properties) {
        this.trustedProxies = new HashSet<>(properties.getTrustedProxies());
    }

    /**
     * 解析安全边界内的客户端 IP；仅当直接来源属于可信代理时才读取
     * {@code X-Forwarded-For}，并从代理链末端跳过可信节点。
     *
     * @param request 当前 HTTP 请求
     * @return 清洗并限制长度后的客户端 IP，无法识别时返回 {@code unknown}
     */
    public String resolve(HttpServletRequest request) {
        String remote = safe(request.getRemoteAddr());
        if (!trustedProxies.contains(remote)) return remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        String[] addresses = forwarded.split(",");
        for (int i = addresses.length - 1; i >= 0; i--) {
            String candidate = safe(addresses[i].trim());
            if (!trustedProxies.contains(candidate)) return candidate;
        }
        return remote;
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String sanitized = value.replaceAll("[^0-9a-fA-F:.,]", "");
        return sanitized.substring(0, Math.min(64, sanitized.length()));
    }
}
