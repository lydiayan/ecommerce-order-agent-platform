package com.css.mallorderagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SessionGuardFilterTest {

    private static final String CONCURRENT_SESSION_EXPIRED =
            "org.springframework.session.security.SpringSessionBackedSessionInformation.EXPIRED";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsSessionExpiredByConcurrentLoginBeforeControllerRuns() throws Exception {
        SecurityUserPrincipal principal = new SecurityUserPrincipal(
                8L, "customer.zhangwei", null, "USER1001", "张伟",
                Set.of("CUSTOMER"), List.of("ROLE_CUSTOMER"),
                true, true, false, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CONCURRENT_SESSION_EXPIRED, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new SessionGuardFilter(mock(AppUserRepository.class), new AuthProperties(), new ObjectMapper())
                .doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertTrue(response.getContentAsString().contains("该账号已在其他位置登录"));
        assertTrue(session.isInvalid());
        assertNull(chain.getRequest());
    }
}
