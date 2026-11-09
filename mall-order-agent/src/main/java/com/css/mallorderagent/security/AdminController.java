package com.css.mallorderagent.security;

import com.css.mallorderagent.demo.DemoPersonaCategory;
import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.demo.DemoPersonaView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Pattern USERNAME = Pattern.compile("[a-z][a-z0-9._-]{3,63}");
    private static final Set<String> TOKEN_SCOPES = Set.of("EVALUATION_ACT_AS");

    private final AppUserRepository users;
    private final ApiTokenRepository tokens;
    private final DemoPersonaService identities;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityAuditService audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminController(AppUserRepository users, ApiTokenRepository tokens,
                           DemoPersonaService identities, PasswordEncoder passwordEncoder,
                           PasswordPolicy passwordPolicy, SecurityAuditService audit) {
        this.users = users;
        this.tokens = tokens;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
    }

    /**
     * 查询系统中的全部登录账户，供管理员维护账户状态和业务身份绑定。
     *
     * @return 账户列表及其角色、启用状态和锁定信息
     */
    @GetMapping("/users")
    public Map<String, Object> users() {
        return success(users.findAll());
    }

    /**
     * 查询可绑定给登录账户的全部演示业务身份。
     *
     * @return 演示身份、类别和能力列表
     */
    @GetMapping("/identities")
    public Map<String, Object> identities() {
        return success(identities.findAll());
    }

    /**
     * 创建登录账户，并根据选定的业务身份自动分配角色。
     *
     * @param input 用户名、业务身份编号和首次登录使用的临时密码
     * @param principal 当前登录的管理员身份
     * @param request 当前 HTTP 请求，用于记录账户创建审计信息
     * @return 新账户的主键和规范化用户名
     */
    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody CreateUserRequest input,
                                          @AuthenticationPrincipal SecurityUserPrincipal principal,
                                          HttpServletRequest request) {
        if (input == null || input.username() == null || input.actorUserId() == null
                || input.temporaryPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名、身份和临时密码不能为空");
        }
        String username = input.username().trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名格式不正确");
        }
        DemoPersonaView identity = identities.requirePersona(input.actorUserId().trim());
        String role = roleFor(identity.category());
        passwordPolicy.validate(username, input.temporaryPassword());
        try {
            long userId = users.createUser(username, passwordEncoder.encode(input.temporaryPassword()),
                    identity.actorUserId(), role);
            audit.record("ACCOUNT_CREATE", principal.getUsername(), username,
                    "SUCCESS", role, request);
            return success(Map.of("userId", userId, "username", username));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名或业务身份已被使用");
        }
    }

    /**
     * 启用或停用指定登录账户；不允许管理员停用自己的当前账户。
     *
     * @param userId 目标账户主键
     * @param input 目标启用状态
     * @param principal 当前登录的管理员身份
     * @param request 当前 HTTP 请求，用于记录账户状态变更审计信息
     * @return 通用成功响应
     */
    @PostMapping("/users/{userId}/enabled")
    public Map<String, Object> setEnabled(@PathVariable long userId, @RequestBody EnabledRequest input,
                                          @AuthenticationPrincipal SecurityUserPrincipal principal,
                                          HttpServletRequest request) {
        AppUserRepository.UserRow target = requireUser(userId);
        if (principal.userId() == userId && !input.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能停用当前管理员账户");
        }
        users.setEnabled(userId, input.enabled());
        audit.record(input.enabled() ? "ACCOUNT_ENABLE" : "ACCOUNT_DISABLE",
                principal.getUsername(), target.username(), "SUCCESS", null, request);
        return success(null);
    }

    /**
     * 清除指定账户因连续登录失败产生的锁定状态。
     *
     * @param userId 目标账户主键
     * @param principal 当前登录的管理员身份
     * @param request 当前 HTTP 请求，用于记录账户解锁审计信息
     * @return 通用成功响应
     */
    @PostMapping("/users/{userId}/unlock")
    public Map<String, Object> unlock(@PathVariable long userId,
                                      @AuthenticationPrincipal SecurityUserPrincipal principal,
                                      HttpServletRequest request) {
        AppUserRepository.UserRow target = requireUser(userId);
        users.unlock(userId);
        audit.record("ACCOUNT_UNLOCK", principal.getUsername(), target.username(),
                "SUCCESS", null, request);
        return success(null);
    }

    /**
     * 将指定账户密码重置为临时密码，并要求该用户下次登录后修改。
     *
     * @param userId 目标账户主键
     * @param input 符合密码策略的新临时密码
     * @param principal 当前登录的管理员身份
     * @param request 当前 HTTP 请求，用于记录密码重置审计信息
     * @return 通用成功响应
     */
    @PostMapping("/users/{userId}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable long userId,
                                             @RequestBody ResetPasswordRequest input,
                                             @AuthenticationPrincipal SecurityUserPrincipal principal,
                                             HttpServletRequest request) {
        AppUserRepository.UserRow target = requireUser(userId);
        passwordPolicy.validate(target.username(), input.temporaryPassword());
        users.changePassword(userId, passwordEncoder.encode(input.temporaryPassword()), true);
        audit.record("PASSWORD_RESET", principal.getUsername(), target.username(),
                "SUCCESS", null, request);
        return success(null);
    }

    /**
     * 查询管理员创建的 API 令牌元数据，不返回令牌明文。
     *
     * @return API 令牌名称、范围、有效期和撤销状态列表
     */
    @GetMapping("/tokens")
    public Map<String, Object> tokens() {
        return success(tokens.findAll());
    }

    /**
     * 创建指定范围和有效期的评测 API 令牌；令牌明文只在本次响应中返回。
     *
     * @param input 令牌名称、权限范围和有效天数（限制在 1 至 365 天）
     * @param principal 当前登录的管理员身份
     * @param request 当前 HTTP 请求，用于记录令牌创建审计信息
     * @return 新令牌主键和仅显示一次的令牌明文
     */
    @PostMapping("/tokens")
    public Map<String, Object> createToken(@RequestBody CreateTokenRequest input,
                                           @AuthenticationPrincipal SecurityUserPrincipal principal,
                                           HttpServletRequest request) {
        if (input == null || input.name() == null || input.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "令牌名称不能为空");
        }
        String scope = input.scope() != null ? input.scope().trim() : "";
        if (!TOKEN_SCOPES.contains(scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的令牌范围");
        }
        int validDays = Math.max(1, Math.min(input.validDays(), 365));
        String rawToken = "eat_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(secureRandom.generateSeed(32));
        long tokenId = tokens.create(input.name().trim(), rawToken, scope,
                LocalDateTime.now().plusDays(validDays));
        audit.record("API_TOKEN_CREATE", principal.getUsername(), Long.toString(tokenId),
                "SUCCESS", scope, request);
        return success(Map.of("id", tokenId, "token", rawToken,
                "message", "该令牌仅显示一次，请立即配置到评测系统"));
    }

    /**
     * 撤销指定 API 令牌，使其不能再访问内部评测接口。
     *
     * @param tokenId 目标 API 令牌主键
     * @param principal 当前登录的管理员身份
     * @param request 当前 HTTP 请求，用于记录令牌撤销审计信息
     * @return 通用成功响应
     */
    @PostMapping("/tokens/{tokenId}/revoke")
    public Map<String, Object> revokeToken(@PathVariable long tokenId,
                                           @AuthenticationPrincipal SecurityUserPrincipal principal,
                                           HttpServletRequest request) {
        if (!tokens.revoke(tokenId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "令牌不存在或已撤销");
        }
        audit.record("API_TOKEN_REVOKE", principal.getUsername(), Long.toString(tokenId),
                "SUCCESS", null, request);
        return success(null);
    }

    /**
     * 查询最近的安全审计事件，供管理员追踪账户和令牌操作。
     *
     * @param limit 最大返回条数，默认 100
     * @return 按时间倒序排列的安全审计事件
     */
    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestParam(defaultValue = "100") int limit) {
        return success(audit.latest(limit));
    }

    private AppUserRepository.UserRow requireUser(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在"));
    }

    private static String roleFor(DemoPersonaCategory category) {
        return switch (category) {
            case HR -> "HR";
            case ENGINEERING -> "ENGINEERING";
            case SALES -> "SALES";
            case CUSTOMER -> "CUSTOMER";
        };
    }

    private static Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data != null ? data : Map.of());
    }

    public record CreateUserRequest(String username, String actorUserId, String temporaryPassword) { }
    public record EnabledRequest(boolean enabled) { }
    public record ResetPasswordRequest(String temporaryPassword) { }
    public record CreateTokenRequest(String name, String scope, int validDays) { }
}
