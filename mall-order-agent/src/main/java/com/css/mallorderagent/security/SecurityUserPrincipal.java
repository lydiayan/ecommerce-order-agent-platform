package com.css.mallorderagent.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.CredentialsContainer;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class SecurityUserPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String username;
    private String passwordHash;
    private final String actorUserId;
    private final String displayName;
    private final Set<String> roles;
    private final List<String> authorityNames;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final boolean passwordChangeRequired;
    private final long authVersion;

    public SecurityUserPrincipal(long userId, String username, String passwordHash,
                                 String actorUserId, String displayName, Set<String> roles,
                                 List<String> authorityNames, boolean enabled,
                                 boolean accountNonLocked, boolean passwordChangeRequired,
                                 long authVersion) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.actorUserId = actorUserId;
        this.displayName = displayName;
        this.roles = Set.copyOf(roles);
        this.authorityNames = List.copyOf(authorityNames);
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.passwordChangeRequired = passwordChangeRequired;
        this.authVersion = authVersion;
    }

    public long userId() { return userId; }
    public String actorUserId() { return actorUserId; }
    public String displayName() { return displayName; }
    public Set<String> roles() { return roles; }
    public boolean passwordChangeRequired() { return passwordChangeRequired; }
    public long authVersion() { return authVersion; }
    public boolean isBusinessUser() { return actorUserId != null && !actorUserId.isBlank(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorityNames.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public void eraseCredentials() { passwordHash = null; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
