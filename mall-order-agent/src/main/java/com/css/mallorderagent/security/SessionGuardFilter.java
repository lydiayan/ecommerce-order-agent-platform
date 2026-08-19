package com.css.mallorderagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public class SessionGuardFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_AT = "AUTHENTICATED_AT";
    private static final String CONCURRENT_SESSION_EXPIRED =
            "org.springframework.session.security.SpringSessionBackedSessionInformation.EXPIRED";
    private final AppUserRepository users;
    private final AuthProperties properties;
    private final ObjectMapper objectMapper;

    public SessionGuardFilter(AppUserRepository users, AuthProperties properties, ObjectMapper objectMapper) {
        this.users = users;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUserPrincipal principal) {
            HttpSession session = request.getSession(false);
            if (session != null && Boolean.TRUE.equals(session.getAttribute(CONCURRENT_SESSION_EXPIRED))) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                writeError(response, 401, "该账号已在其他位置登录");
                return;
            }
            Long authenticatedAt = session != null ? (Long) session.getAttribute(AUTHENTICATED_AT) : null;
            boolean expired = authenticatedAt == null
                    || Instant.now().getEpochSecond() - authenticatedAt > properties.getAbsoluteSessionSeconds();
            boolean stale = users.currentAuthVersion(principal.userId()) != principal.authVersion();
            Long impersonationExpiresAt = session != null
                    ? (Long) session.getAttribute(ImpersonationService.EXPIRES_AT) : null;
            boolean impersonationExpired = ImpersonationService.isImpersonating(principal)
                    && impersonationExpiresAt != null
                    && Instant.now().getEpochSecond() > impersonationExpiresAt;
            if (impersonationExpired) {
                ImpersonationService.restore(session);
                writeError(response, 401, "演示身份已到期，已恢复管理员身份");
                return;
            }
            if (expired || stale) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                writeError(response, 401, expired ? "登录已超过最长有效期" : "账户权限已更新，请重新登录");
                return;
            }
            if (principal.passwordChangeRequired() && !allowedDuringPasswordChange(request)) {
                writeError(response, 428, "必须先修改初始或临时密码");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean allowedDuringPasswordChange(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/change-password.html") || path.equals("/change-password.js")
                || path.equals("/style.css") || path.equals("/auth/me")
                || path.equals("/auth/csrf") || path.equals("/auth/change-password")
                || path.equals("/auth/logout");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of("code", status, "message", message));
    }
}
