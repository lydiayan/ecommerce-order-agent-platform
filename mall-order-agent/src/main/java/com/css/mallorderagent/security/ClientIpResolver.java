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
