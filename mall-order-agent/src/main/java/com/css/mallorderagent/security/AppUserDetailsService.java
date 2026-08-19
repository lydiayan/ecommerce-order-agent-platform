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
