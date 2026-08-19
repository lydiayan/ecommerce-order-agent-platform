package com.css.mallorderagent.security;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class PasswordPolicy {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password1234", "123456789012", "qwerty123456", "admin12345678",
            "demo12345678", "password@123");

    public void validate(String username, String password) {
        if (password == null || password.length() < 12 || password.length() > 72) {
            throw new IllegalArgumentException("密码长度必须为12至72个字符");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(normalized)) {
            throw new IllegalArgumentException("不能使用常见弱密码");
        }
        if (username != null && username.length() >= 4
                && normalized.contains(username.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("密码不能包含用户名");
        }
    }
}
