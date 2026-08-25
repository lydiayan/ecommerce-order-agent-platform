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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getRequestURI().startsWith("/internal/evaluation/")
                || request.getRequestURI().startsWith("/internal/feedback/"));
    }

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
