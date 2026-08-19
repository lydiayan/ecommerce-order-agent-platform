package com.css.mallorderagent.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenAuthenticationFilterTest {

    @Mock private ApiTokenRepository tokens;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyActiveScopedToken() throws Exception {
        when(tokens.findActive("valid-token")).thenReturn(Optional.of(
                new ApiTokenRepository.TokenRecord(1L, "agent-insight",
                        List.of("EVALUATION_ACT_AS"), null)));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/internal/evaluation/ask");
        request.addHeader("Authorization", "Bearer valid-token");

        new ApiTokenAuthenticationFilter(tokens).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("agent-insight", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals("EVALUATION_ACT_AS", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void leavesRequestUnauthenticatedForUnknownToken() throws Exception {
        when(tokens.findActive("invalid")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/internal/evaluation/ask");
        request.addHeader("Authorization", "Bearer invalid");

        new ApiTokenAuthenticationFilter(tokens).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
