package com.css.mallorderagent.controller;

import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.HumanFeedbackRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.css.mallorderagent.service.OrderAgentService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderAgentControllerTest {

    @Mock
    private OrderAgentService orderAgentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityUserPrincipal principal = new SecurityUserPrincipal(
                1L, "customer.zhangwei", "", "USER1001", "张伟",
                Set.of("CUSTOMER"), List.of("ROLE_CUSTOMER"), true, true, false, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderAgentController(orderAgentService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void health_returnsRunningMessage() throws Exception {
        mockMvc.perform(get("/agent/order/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("Order agent is running"));
    }

    @Test
    void ask_delegatesToServiceAndWrapsResponse() throws Exception {
        OrderAgentResponse agentResponse = new OrderAgentResponse();
        agentResponse.setQuery("我的订单状态？");
        agentResponse.setAnswer("您有 1 笔待发货订单。");
        agentResponse.setConversationId("conv-001");
        agentResponse.setPlanStrategy("RAG_QA");
        agentResponse.setGrounded(true);
        when(orderAgentService.ask(any(AskRequest.class), eq("USER1001"), eq(true))).thenReturn(agentResponse);

        AskRequest request = new AskRequest();
        request.setQuery("我的订单状态？");
        request.setConversationId("conv-001");
        request.setUserId("USER1005");

        mockMvc.perform(post("/agent/order/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.query").value("我的订单状态？"))
                .andExpect(jsonPath("$.data.answer").value("您有 1 笔待发货订单。"))
                .andExpect(jsonPath("$.data.conversationId").value("conv-001"))
                .andExpect(jsonPath("$.data.planStrategy").value("RAG_QA"))
                .andExpect(jsonPath("$.data.grounded").value(true));

        ArgumentCaptor<AskRequest> captor = ArgumentCaptor.forClass(AskRequest.class);
        verify(orderAgentService).ask(captor.capture(), eq("USER1001"), eq(true));
        assertEquals("我的订单状态？", captor.getValue().getQuery());
        assertEquals("conv-001", captor.getValue().getConversationId());
        assertEquals("USER1005", captor.getValue().getUserId());
    }

    @Test
    void ask_whenInterrupted_returnsInterruptFields() throws Exception {
        OrderAgentResponse agentResponse = new OrderAgentResponse();
        agentResponse.setInterrupted(true);
        agentResponse.setThreadId("thread-abc");
        agentResponse.setInterruptMessage("需要人工审核");
        agentResponse.setOperationLabel("退款");
        agentResponse.setApprovalReason("敏感操作");
        agentResponse.setAwaitingUserConfirm(true);
        when(orderAgentService.ask(any(AskRequest.class), eq("USER1001"), eq(true))).thenReturn(agentResponse);

        AskRequest request = new AskRequest();
        request.setQuery("帮我退款 ORD001");
        request.setConversationId("conv-002");

        mockMvc.perform(post("/agent/order/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.interrupted").value(true))
                .andExpect(jsonPath("$.data.threadId").value("thread-abc"))
                .andExpect(jsonPath("$.data.interruptMessage").value("需要人工审核"))
                .andExpect(jsonPath("$.data.operationLabel").value("退款"))
                .andExpect(jsonPath("$.data.approvalReason").value("敏感操作"))
                .andExpect(jsonPath("$.data.awaitingUserConfirm").value(true));
    }

    @Test
    void resume_approved_delegatesToService() throws Exception {
        OrderAgentResponse agentResponse = new OrderAgentResponse();
        agentResponse.setAnswer("已继续执行");
        agentResponse.setThreadId("thread-xyz");
        agentResponse.setInterrupted(false);
        when(orderAgentService.resume(any(HumanFeedbackRequest.class), eq("USER1001"))).thenReturn(agentResponse);

        HumanFeedbackRequest request = new HumanFeedbackRequest();
        request.setThreadId("thread-xyz");
        request.setApproved(true);

        mockMvc.perform(post("/agent/order/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("已继续执行"))
                .andExpect(jsonPath("$.data.threadId").value("thread-xyz"))
                .andExpect(jsonPath("$.data.interrupted").value(false));

        ArgumentCaptor<HumanFeedbackRequest> captor = ArgumentCaptor.forClass(HumanFeedbackRequest.class);
        verify(orderAgentService).resume(captor.capture(), eq("USER1001"));
        assertEquals("thread-xyz", captor.getValue().getThreadId());
        assertEquals(true, captor.getValue().getApproved());
    }

    @Test
    void resume_rejectedWithRevisedQuery_delegatesToService() throws Exception {
        OrderAgentResponse agentResponse = new OrderAgentResponse();
        agentResponse.setAnswer("已按修改后的问题重新规划");
        agentResponse.setQuery("查询订单 ORD001 物流");
        when(orderAgentService.resume(any(HumanFeedbackRequest.class), eq("USER1001"))).thenReturn(agentResponse);

        HumanFeedbackRequest request = new HumanFeedbackRequest();
        request.setThreadId("thread-rej");
        request.setApproved(false);
        request.setRevisedQuery("查询订单 ORD001 物流");

        mockMvc.perform(post("/agent/order/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("已按修改后的问题重新规划"))
                .andExpect(jsonPath("$.data.query").value("查询订单 ORD001 物流"));

        ArgumentCaptor<HumanFeedbackRequest> captor = ArgumentCaptor.forClass(HumanFeedbackRequest.class);
        verify(orderAgentService).resume(captor.capture(), eq("USER1001"));
        assertEquals(false, captor.getValue().getApproved());
        assertEquals("查询订单 ORD001 物流", captor.getValue().getRevisedQuery());
    }
}
