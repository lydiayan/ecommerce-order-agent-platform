package com.css.mallorderagent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private final ApiTokenRepository tokenRepository;

    public ApiTokenAuthenticationFilter(ApiTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 仅对内部评测和反馈接口启用 API Token 认证。
     *
     * @param request 当前请求
     * @return 不属于内部接口时返回 {@code true}
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getRequestURI().startsWith("/internal/evaluation/")
                || request.getRequestURI().startsWith("/internal/feedback/"));
    }

    /**
     * 解析 Bearer Token，并把有效 Token 的 scopes 转换为当前请求的授权能力。
     * 无效或缺失 Token 不直接返回错误，后续由 Spring Security 授权规则拒绝。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenRepository.findActive(authorization.substring(7)).ifPresent(token -> {
                var authorities = token.scopes().stream().map(SimpleGrantedAuthority::new).toList();
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        token.name(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }
}
