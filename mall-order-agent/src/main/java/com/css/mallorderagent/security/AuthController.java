package com.css.mallorderagent.security;

import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.demo.DemoPersonaView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AppUserRepository users;
    private final DemoPersonaService identities;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityAuditService auditService;
    private final AuthProperties authProperties;

    public AuthController(AppUserRepository users, DemoPersonaService identities,
                          PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                          SecurityAuditService auditService, AuthProperties authProperties) {
        this.users = users;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
        this.authProperties = authProperties;
    }

    /**
     * 获取当前会话的 CSRF 令牌，供浏览器在后续写请求中携带。
     *
     * @param token Spring Security 为当前请求生成或恢复的 CSRF 令牌
     * @return CSRF 请求头名称和令牌值
     */
    @GetMapping("/csrf")
    public Map<String, Object> csrf(CsrfToken token) {
        return Map.of("code", 200, "message", "success", "data", Map.of(
                "headerName", token.getHeaderName(), "token", token.getToken()));
    }

    /**
     * 查询当前登录账户、业务身份、角色以及演示身份代入状态。
     *
     * @param principal 当前登录身份
     * @param request 当前 HTTP 请求，用于读取会话中的身份代入到期时间
     * @return 当前用户及会话安全状态
     */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                  HttpServletRequest request) {
        DemoPersonaView identity = principal.isBusinessUser()
                ? identities.requirePersona(principal.actorUserId()) : null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", principal.getUsername());
        data.put("displayName", principal.displayName() != null ? principal.displayName() : principal.getUsername());
        data.put("actorUserId", principal.actorUserId());
        data.put("roles", principal.roles());
        data.put("passwordChangeRequired", principal.passwordChangeRequired());
        data.put("persona", identity);
        HttpSession session = request.getSession(false);
        data.put("impersonating", ImpersonationService.isImpersonating(principal));
        data.put("demoImpersonationEnabled", authProperties.isDemoImpersonationEnabled());
        data.put("impersonationExpiresAt", session != null
                ? session.getAttribute(ImpersonationService.EXPIRES_AT) : null);
        return Map.of("code", 200, "message", "success", "data", data);
    }

    /**
     * 校验当前密码和密码策略后修改登录密码，并使当前会话失效。
     *
     * @param principal 当前登录身份，用于定位待修改账户
     * @param request 当前密码和新密码
     * @param servletRequest 当前 HTTP 请求，用于记录审计并注销会话
     * @return 密码修改成功并提示重新登录的响应
     */
    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                              @RequestBody ChangePasswordRequest request,
                                              HttpServletRequest servletRequest) {
        AppUserRepository.UserRow current = users.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (request == null || !passwordEncoder.matches(request.currentPassword(), current.passwordHash())) {
            auditService.record("PASSWORD_CHANGE", principal.getUsername(), null,
                    "DENIED", "current_password_mismatch", servletRequest);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        passwordPolicy.validate(principal.getUsername(), request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), current.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        users.changePassword(principal.userId(), passwordEncoder.encode(request.newPassword()), false);
        auditService.record("PASSWORD_CHANGE", principal.getUsername(), null,
                "SUCCESS", null, servletRequest);
        HttpSession session = servletRequest.getSession(false);
        if (session != null) session.invalidate();
        return Map.of("code", 200, "message", "密码已修改，请重新登录");
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) { }
}
