package com.css.mallorderagent.feedback;

import com.css.mallorderagent.config.FeedbackProperties;
import com.css.mallorderagent.dto.AgentFeedbackRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentFeedbackServiceTest {

    @Mock
    private AgentFeedbackRepository repository;

    private FeedbackCrypto crypto;
    private AgentFeedbackService service;

    @BeforeEach
    void setUp() {
        FeedbackProperties properties = new FeedbackProperties();
        properties.setEncryptionKey("0123456789abcdef-feedback-key");
        properties.setRetentionDays(90);
        properties.setAgentVersion("test-version");
        properties.setModelName("test-model");
        crypto = new FeedbackCrypto(properties);
        crypto.initialize();
        service = new AgentFeedbackService(repository, crypto, new FeedbackSanitizer(),
                properties, new ObjectMapper());
    }

    @Test
    void registerResponse_persistsEncryptedSanitizedSnapshotBeforeEnablingFeedback() {
        OrderAgentResponse response = response();
        response.setQuery("联系 13812345678，Authorization: Bearer abcdefghijklmnop");

        service.registerResponse(response, 7L, "USER1001", true);

        assertTrue(response.isFeedbackEnabled());
        assertNotNull(response.getResponseId());
        ArgumentCaptor<AgentFeedbackRepository.ResponseSnapshotInsert> captor =
                ArgumentCaptor.forClass(AgentFeedbackRepository.ResponseSnapshotInsert.class);
        verify(repository).insertResponse(captor.capture());
        AgentFeedbackRepository.ResponseSnapshotInsert saved = captor.getValue();
        assertEquals("test-version", saved.agentVersion());
        assertEquals("test-model", saved.modelName());
        assertTrue(crypto.decrypt(saved.queryCiphertext()).contains("[PHONE]"));
        assertTrue(crypto.decrypt(saved.queryCiphertext()).contains("[REDACTED]"));
        assertFalse(saved.answerCiphertext().contains(response.getAnswer()));
    }

    @Test
    void registerResponse_whenPersistenceFailsLeavesAnswerUsableButFeedbackDisabled() {
        OrderAgentResponse response = response();
        org.mockito.Mockito.doThrow(new IllegalStateException("db unavailable"))
                .when(repository).insertResponse(any());

        service.registerResponse(response, 7L, "USER1001", true);

        assertFalse(response.isFeedbackEnabled());
        assertEquals(null, response.getResponseId());
        assertEquals("可以退款。", response.getAnswer());
    }

    @Test
    void registerResponse_truncatesOversizedToolSnapshotWithoutDisablingFeedback() {
        OrderAgentResponse response = response();
        response.setToolSummary("x".repeat(25_000));

        service.registerResponse(response, 7L, "USER1001", true);

        ArgumentCaptor<AgentFeedbackRepository.ResponseSnapshotInsert> captor =
                ArgumentCaptor.forClass(AgentFeedbackRepository.ResponseSnapshotInsert.class);
        verify(repository).insertResponse(captor.capture());
        assertTrue(response.isFeedbackEnabled());
        assertEquals(20_000, crypto.decrypt(captor.getValue().toolSummaryCiphertext()).length());
    }

    @Test
    void submitDown_isIdempotentAndCreatesUrgentBadCase() {
        String responseId = "79713549-9e35-4140-8e87-1fd7ac72c387";
        when(repository.responseOwnedBy(responseId, 7L)).thenReturn(true);
        when(repository.findFeedback(responseId, 7L)).thenReturn(Optional.empty());
        when(repository.upsertFeedback(eq(responseId), eq(7L), eq("DOWN"), any(), any()))
                .thenReturn(new AgentFeedbackRepository.FeedbackRow(
                        12L, "DOWN", "[\"TOOL_FAILURE\"]", crypto.encrypt("工具没有执行"), LocalDateTime.now()));
        when(repository.findBadCaseIdentity(responseId)).thenReturn(Optional.empty());
        when(repository.openBadCase(responseId, "URGENT"))
                .thenReturn(new AgentFeedbackRepository.BadCaseIdentity(21L, "NEW"));

        AgentFeedbackService.FeedbackView result = service.submit(new AgentFeedbackRequest(
                responseId, "DOWN", List.of("TOOL_FAILURE"), "工具没有执行"), 7L);

        assertEquals("DOWN", result.rating());
        assertEquals(List.of("TOOL_FAILURE"), result.reasons());
        verify(repository).insertFeedbackHistory(eq(responseId), eq(7L), eq("CREATE"),
                eq(null), eq("DOWN"), any());
        verify(repository).insertBadCaseHistory(eq(21L), eq(7L), eq(null), eq("NEW"), any());
    }

    @Test
    void submitUp_afterDownIgnoresOnlyUntriagedBadCase() {
        String responseId = "79713549-9e35-4140-8e87-1fd7ac72c387";
        AgentFeedbackRepository.FeedbackRow previous = new AgentFeedbackRepository.FeedbackRow(
                12L, "DOWN", "[]", null, LocalDateTime.now());
        when(repository.responseOwnedBy(responseId, 7L)).thenReturn(true);
        when(repository.findFeedback(responseId, 7L)).thenReturn(Optional.of(previous));
        when(repository.upsertFeedback(responseId, 7L, "UP", "[]", null))
                .thenReturn(new AgentFeedbackRepository.FeedbackRow(
                        12L, "UP", "[]", null, LocalDateTime.now()));
        when(repository.ignoreNewBadCase(responseId))
                .thenReturn(Optional.of(new AgentFeedbackRepository.BadCaseIdentity(21L, "NEW")));

        AgentFeedbackService.FeedbackView result = service.submit(
                new AgentFeedbackRequest(responseId, "UP", List.of("SAFETY_RISK"), "ignored"), 7L);

        assertEquals("UP", result.rating());
        assertTrue(result.reasons().isEmpty());
        verify(repository).insertBadCaseHistory(eq(21L), eq(7L), eq("NEW"), eq("IGNORED"), any());
    }

    @Test
    void submit_rejectsFeedbackForAnotherUsersResponse() {
        String responseId = "79713549-9e35-4140-8e87-1fd7ac72c387";
        when(repository.responseOwnedBy(responseId, 7L)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> service.submit(
                new AgentFeedbackRequest(responseId, "DOWN", List.of(), null), 7L));

        verify(repository, never()).upsertFeedback(any(), eq(7L), any(), any(), any());
    }

    private static OrderAgentResponse response() {
        OrderAgentResponse response = new OrderAgentResponse();
        response.setQuery("订单可以退款吗？");
        response.setAnswer("可以退款。");
        response.setConversationId("conv-1");
        response.setTraceId("trace-1");
        response.setPlanStrategy("RAG_QA");
        response.setGrounded(true);
        return response;
    }
}
