package com.css.mallorderagent.security;

import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.demo.DemoPersonaView;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Profile("demo")
public class ImpersonationService {

    public static final String ORIGINAL_PRINCIPAL = "IMPERSONATION_ORIGINAL_PRINCIPAL";
    public static final String EXPIRES_AT = "IMPERSONATION_EXPIRES_AT";
    public static final String REASON = "IMPERSONATION_REASON";
    private static final long DURATION_SECONDS = 900;

    private final DemoPersonaService identities;

    public ImpersonationService(DemoPersonaService identities) {
        this.identities = identities;
    }

    public void start(SecurityUserPrincipal admin, String actorUserId, String reason, HttpSession session) {
        DemoPersonaView identity = identities.requirePersona(actorUserId);
        String role = identity.category().name();
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + role);
        identity.capabilities().forEach(capability -> authorities.add(capability.name()));
        authorities.add("IMPERSONATED");
        SecurityUserPrincipal assumed = new SecurityUserPrincipal(
                admin.userId(), admin.getUsername(), admin.getPassword(), identity.actorUserId(),
                identity.displayName(), Set.of(role), authorities, true, true, false, admin.authVersion());
        session.setAttribute(ORIGINAL_PRINCIPAL, admin);
        session.setAttribute(EXPIRES_AT, Instant.now().plusSeconds(DURATION_SECONDS).getEpochSecond());
        session.setAttribute(REASON, reason);
        replaceAuthentication(session, assumed);
    }

    public static boolean isImpersonating(SecurityUserPrincipal principal) {
        return principal != null && principal.getAuthorities().stream()
                .anyMatch(authority -> "IMPERSONATED".equals(authority.getAuthority()));
    }

    public static boolean restore(HttpSession session) {
        if (session == null || !(session.getAttribute(ORIGINAL_PRINCIPAL) instanceof SecurityUserPrincipal original)) {
            return false;
        }
        replaceAuthentication(session, original);
        session.removeAttribute(ORIGINAL_PRINCIPAL);
        session.removeAttribute(EXPIRES_AT);
        session.removeAttribute(REASON);
        return true;
    }

    private static void replaceAuthentication(HttpSession session, SecurityUserPrincipal principal) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
