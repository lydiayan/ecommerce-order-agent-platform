package com.css.mallorderagent.playground;

import com.css.mallorderagent.demo.DemoCapability;
import com.css.mallorderagent.demo.DemoPersonaCategory;
import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.demo.DemoPersonaView;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.css.mallorderagent.memory.ConversationTurn;
import com.css.mallorderagent.service.OrderAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPlaygroundServiceTest {

    @Mock private OrderAgentService orderAgentService;
    @Mock private DemoPersonaService identities;

    private AdminPlaygroundService service;

    @BeforeEach
    void setUp() {
        service = new AdminPlaygroundService(orderAgentService, identities);
        when(identities.requirePersona("USER1001")).thenReturn(persona("USER1001", "客户甲"));
    }

    @Test
    void keepsHistoryInTheTemporarySessionAndClampsTopK() {
        when(orderAgentService.askForPlayground(any(), eq("USER1001"), anyList(), eq(7L), eq(false)))
                .thenReturn(response("admin-playground-1", "第一轮回答"))
                .thenReturn(response("admin-playground-1", "第二轮回答"));

        service.ask(7L, "USER1001", "第一轮问题", "admin-playground-1", 50, false);
        service.ask(7L, "USER1001", "第二轮问题", "admin-playground-1", 0, false);

        ArgumentCaptor<AskRequest> requests = ArgumentCaptor.forClass(AskRequest.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConversationTurn>> histories = ArgumentCaptor.forClass(List.class);
        verify(orderAgentService, times(2)).askForPlayground(
                requests.capture(), eq("USER1001"), histories.capture(), eq(7L), eq(false));

        assertEquals(10, requests.getAllValues().get(0).getTopK());
        assertEquals(1, requests.getAllValues().get(1).getTopK());
        assertEquals(0, histories.getAllValues().get(0).size());
        assertEquals(1, histories.getAllValues().get(1).size());
        assertEquals("第一轮问题", histories.getAllValues().get(1).get(0).userMessage());
        assertEquals("第一轮回答", histories.getAllValues().get(1).get(0).assistantMessage());
        assertEquals(1, service.sessionCount());
    }

    @Test
    void refusesToChangeIdentityInsideAnExistingSession() {
        when(identities.requirePersona("USER2001")).thenReturn(persona("USER2001", "客户乙"));
        when(orderAgentService.askForPlayground(any(), eq("USER1001"), anyList(), eq(7L), eq(false)))
                .thenReturn(response("admin-playground-1", "回答"));
        service.ask(7L, "USER1001", "问题", "admin-playground-1", 5, false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.ask(7L, "USER2001", "另一个问题", "admin-playground-1", 5, false));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void forwardsWebSearchOnlyForTheRequestedRun() {
        when(orderAgentService.askForPlayground(
                any(), eq("USER1001"), anyList(), eq(7L), eq(true)))
                .thenReturn(response("admin-playground-1", "联网回答"));

        AdminPlaygroundService.PlaygroundResponse result = service.ask(
                7L, "USER1001", "今天有什么新消息", "admin-playground-1", 5, true);

        assertTrue(result.webSearchEnabled());
        verify(orderAgentService).askForPlayground(
                any(), eq("USER1001"), anyList(), eq(7L), eq(true));
    }

    @Test
    void resetClearsLocalAndGraphSessionState() {
        when(orderAgentService.askForPlayground(any(), eq("USER1001"), anyList(), eq(7L), eq(false)))
                .thenReturn(response("admin-playground-1", "回答"));
        service.ask(7L, "USER1001", "问题", "admin-playground-1", 5, false);

        assertEquals(true, service.reset(7L, "USER1001", "admin-playground-1"));
        assertEquals(0, service.sessionCount());
        verify(orderAgentService).clearPlaygroundSession("USER1001", "admin-playground-1", 7L);
    }

    @Test
    void resetCannotClearAConversationUsingAnotherIdentity() {
        when(identities.requirePersona("USER2001")).thenReturn(persona("USER2001", "客户乙"));
        when(orderAgentService.askForPlayground(any(), eq("USER1001"), anyList(), eq(7L), eq(false)))
                .thenReturn(response("admin-playground-1", "回答"));
        service.ask(7L, "USER1001", "问题", "admin-playground-1", 5, false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.reset(7L, "USER2001", "admin-playground-1"));

        assertEquals(409, error.getStatusCode().value());
        assertEquals(1, service.sessionCount());
    }

    private static DemoPersonaView persona(String actorUserId, String displayName) {
        return new DemoPersonaView(
                actorUserId, DemoPersonaCategory.CUSTOMER, displayName, "客户", "外部",
                "测试身份", "欢迎", List.of("customer_service"), List.of("default"),
                List.of(DemoCapability.KNOWLEDGE_SEARCH), List.of());
    }

    private static OrderAgentResponse response(String conversationId, String answer) {
        OrderAgentResponse response = new OrderAgentResponse();
        response.setConversationId(conversationId);
        response.setAnswer(answer);
        response.setPlanStrategy("RAG_QA");
        return response;
    }
}
