package com.css.mallorderagent.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("demo")
public class CredentialSeeder implements ApplicationRunner {

    private static final List<SeedUser> DEMO_USERS = List.of(
            new SeedUser("hr.linyue", "HR001", "HR"),
            new SeedUser("hr.chenchen", "HR002", "HR"),
            new SeedUser("dev.zhouhang", "DEV001", "ENGINEERING"),
            new SeedUser("dev.zhaoning", "DEV002", "ENGINEERING"),
            new SeedUser("sales.wanglei", "SALES001", "SALES"),
            new SeedUser("sales.liuting", "SALES002", "SALES"),
            new SeedUser("customer.zhangwei", "USER1001", "CUSTOMER"),
            new SeedUser("customer.lina", "USER1002", "CUSTOMER")
    );

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public CredentialSeeder(AppUserRepository users,
                            PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String demoPassword = properties.getDemoInitialPassword();
        validatePassword(demoPassword, "DEMO_INITIAL_PASSWORD");
        String demoHash = passwordEncoder.encode(demoPassword);
        DEMO_USERS.forEach(user -> users.seedUser(
                user.username(), demoHash, user.actorUserId(), user.role(), false));

    }

    private static void validatePassword(String password, String propertyName) {
        if (password == null || password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException(propertyName + " must contain 12 to 72 characters");
        }
    }

    private record SeedUser(String username, String actorUserId, String role) { }
}
