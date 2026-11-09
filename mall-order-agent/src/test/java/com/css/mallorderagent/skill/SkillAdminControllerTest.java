package com.css.mallorderagent.skill;

import com.css.mallorderagent.security.SecurityAuditService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.css.mallorderagent.skill.model.SkillPreview;
import com.css.mallorderagent.skill.model.SkillSourceRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillAdminControllerTest {

    private final SkillManagementService service = mock(SkillManagementService.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityUserPrincipal principal = new SecurityUserPrincipal(
                1L, "admin", "", null, "管理员", Set.of("ADMIN"),
                List.of("ROLE_ADMIN"), true, true, false, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(new SkillAdminController(service, audit))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void previewReturnsDigestRequiredByInstall() throws Exception {
        SkillSourceRequest input = new SkillSourceRequest(
                "https://github.com/example/skills", "main", "refund", null);
        when(service.preview(any())).thenReturn(new SkillPreview(
                "refund-explainer", "Explain refunds", "Explain the result.", "a".repeat(64),
                List.of("REFUND_ELIGIBILITY"), List.of("退款资格"), true,
                List.of("SKILL.md", "agents/ecommerce-order-agent.yaml"), 2, 512));

        mockMvc.perform(post("/admin/skills/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("refund-explainer"))
                .andExpect(jsonPath("$.data.packageDigest").value("a".repeat(64)));

        verify(audit).record(eq("SKILL_PREVIEW"), eq("admin"), eq("refund-explainer"),
                eq("SUCCESS"), eq("a".repeat(64)), any());
    }
}
