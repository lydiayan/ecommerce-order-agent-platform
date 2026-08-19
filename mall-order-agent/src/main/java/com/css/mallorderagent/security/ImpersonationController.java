package com.css.mallorderagent.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@Profile("demo")
public class ImpersonationController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ImpersonationService impersonationService;
    private final SecurityAuditService audit;

    public ImpersonationController(AppUserRepository users, PasswordEncoder passwordEncoder,
                                   ImpersonationService impersonationService, SecurityAuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.impersonationService = impersonationService;
        this.audit = audit;
    }

    @PostMapping("/admin/impersonation")
    public Map<String, Object> start(@RequestBody StartRequest input,
                                     @AuthenticationPrincipal SecurityUserPrincipal principal,
                                     HttpServletRequest request) {
        if (input == null || input.adminPassword() == null || input.actorUserId() == null
                || input.reason() == null || input.reason().trim().length() < 5
                || input.reason().trim().length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "身份、管理员密码和5至200字原因不能为空");
        }
        AppUserRepository.UserRow admin = users.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(input.adminPassword(), admin.passwordHash())) {
            audit.record("IMPERSONATION_START", principal.getUsername(), input.actorUserId(),
                    "DENIED", "reauthentication_failed", request);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理员密码不正确");
        }
        audit.record("IMPERSONATION_START", principal.getUsername(), input.actorUserId(),
                "SUCCESS", input.reason().trim(), request);
        impersonationService.start(principal, input.actorUserId().trim(), input.reason().trim(),
                request.getSession());
        return Map.of("code", 200, "message", "success", "data", Map.of("redirect", "/"));
    }

    @PostMapping("/auth/impersonation/exit")
    public Map<String, Object> exit(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                    HttpServletRequest request) {
        if (!ImpersonationService.isImpersonating(principal)
                || !ImpersonationService.restore(request.getSession(false))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前不在演示身份中");
        }
        audit.record("IMPERSONATION_END", principal.getUsername(), principal.actorUserId(),
                "SUCCESS", null, request);
        return Map.of("code", 200, "message", "success", "data", Map.of("redirect", "/admin.html"));
    }

    public record StartRequest(String actorUserId, String adminPassword, String reason) { }
}
