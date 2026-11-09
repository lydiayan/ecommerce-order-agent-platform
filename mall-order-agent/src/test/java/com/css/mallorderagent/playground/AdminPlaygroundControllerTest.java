package com.css.mallorderagent.playground;

import com.css.mallorderagent.security.SecurityAuditService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
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

class AdminPlaygroundControllerTest {

    private final AdminPlaygroundService service = mock(AdminPlaygroundService.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityUserPrincipal principal = new SecurityUserPrincipal(
                7L, "admin", "", null, "管理员", Set.of("ADMIN"),
                List.of("ROLE_ADMIN"), true, true, false, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminPlaygroundController(service, audit))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void askReturnsAdministratorDiagnosticsAndWritesAuditEvent() throws Exception {
        AdminPlaygroundService.PlaygroundResponse response =
                new AdminPlaygroundService.PlaygroundResponse(
                        "USER1001", "客户甲", "admin-playground-1", "退款规则说明",
                        true, "trace-1", "SKILL_EXECUTE", "REFUND_POLICY", "RULE",
                        0.96D, "MATCH", false,
                        new AdminPlaygroundService.SkillDiagnostic(
                                "refund-explainer", 12L, "a".repeat(64)),
                        "查询退款知识库", null, true, false, "退款", "");
        when(service.ask(7L, "USER1001", "退款规则", "admin-playground-1", 5, true))
                .thenReturn(response);

        mockMvc.perform(post("/admin/playground/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminPlaygroundController.PlaygroundAskRequest(
                                        "USER1001", "退款规则", "admin-playground-1", 5, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("退款规则说明"))
                .andExpect(jsonPath("$.data.planStrategy").value("SKILL_EXECUTE"))
                .andExpect(jsonPath("$.data.skill.name").value("refund-explainer"))
                .andExpect(jsonPath("$.data.skill.versionId").value(12))
                .andExpect(jsonPath("$.data.webSearchEnabled").value(true))
                .andExpect(jsonPath("$.data.toolSummary").value("查询退款知识库"));

        verify(audit).record(eq("ADMIN_PLAYGROUND_ASK"), eq("admin"), eq("USER1001"),
                eq("SUCCESS"), eq("conversation=admin-playground-1,strategy=SKILL_EXECUTE,webSearch=true,trace=trace-1"),
                any());
    }

    @Test
    void resetClearsTheAdministratorsTemporarySession() throws Exception {
        when(service.reset(7L, "USER1001", "admin-playground-1")).thenReturn(true);

        mockMvc.perform(post("/admin/playground/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminPlaygroundController.PlaygroundResetRequest(
                                        "USER1001", "admin-playground-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(true));

        verify(audit).record(eq("ADMIN_PLAYGROUND_RESET"), eq("admin"), eq("USER1001"),
                eq("SUCCESS"), eq("sessionExisted=true"), any());
    }
}
