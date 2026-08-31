package com.css.mallorderagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public LoginThrottleFilter(LoginRateLimiter rateLimiter, ClientIpResolver clientIpResolver,
                               ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    /** @return 不是登录提交请求时返回 {@code true}，跳过来源 IP 限流 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/auth/login".equals(request.getRequestURI()));
    }

    /**
     * 在认证前检查来源 IP 失败次数；达到阈值返回 429，限流存储不可用时返回 503。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (rateLimiter.isBlocked(clientIpResolver.resolve(request))) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(),
                        Map.of("code", 429, "message", "登录尝试过于频繁，请稍后重试"));
                return;
            }
        } catch (RuntimeException e) {
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    Map.of("code", 503, "message", "认证会话服务暂不可用"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
