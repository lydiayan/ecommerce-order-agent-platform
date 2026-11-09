package com.css.mallorderagent.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthCredentialSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final ApiTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public AuthCredentialSeeder(AppUserRepository users, ApiTokenRepository tokens,
                                PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /**
     * 应用启动时按配置幂等创建管理员账户和 AgentInsight 评测 Token。
     * 管理员初始密码存在时必须满足长度约束。
     *
     * @param args 应用启动参数，本实现不读取
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String adminPassword = properties.getAdminInitialPassword();
        if (adminPassword != null && !adminPassword.isBlank()) {
            validatePassword(adminPassword, "ADMIN_INITIAL_PASSWORD");
            users.seedUser("admin", passwordEncoder.encode(adminPassword), null, "ADMIN", true);
        }
        tokens.seed("agent-insight-evaluation", properties.getEvaluationToken(), "EVALUATION_ACT_AS");
    }

    private static void validatePassword(String password, String propertyName) {
        if (password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException(propertyName + " must contain 12 to 72 characters");
        }
    }
}
