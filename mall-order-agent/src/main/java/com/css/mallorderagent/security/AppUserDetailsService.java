package com.css.mallorderagent.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;

    public AppUserDetailsService(AppUserRepository repository) {
        this.repository = repository;
    }

    /**
     * 加载账户、角色能力和业务身份能力，组装 Spring Security 认证主体。
     * 对外统一使用模糊错误信息，避免泄露用户名是否存在。
     *
     * @param username 登录用户名
     * @return 包含账户状态、认证版本和授权能力的主体
     * @throws UsernameNotFoundException 账户不存在时抛出
     */
    @Override
    public SecurityUserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUserRepository.UserRow row = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        Set<String> roles = repository.findRoles(row.id());
        List<String> authorities = new ArrayList<>();
        roles.forEach(role -> authorities.add("ROLE_" + role));
        authorities.addAll(repository.findRoleCapabilities(row.id()));
        authorities.addAll(repository.findActorCapabilities(row.actorUserId()));
        return new SecurityUserPrincipal(
                row.id(), row.username(), row.passwordHash(), row.actorUserId(), row.displayName(), roles,
                authorities.stream().distinct().toList(), row.enabled(), row.accountNonLocked(),
                row.passwordChangeRequired(), row.authVersion());
    }
}
