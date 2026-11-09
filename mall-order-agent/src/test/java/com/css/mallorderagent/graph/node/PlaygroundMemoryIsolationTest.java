package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.memory.ConversationTurn;
import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallordermemory.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlaygroundMemoryIsolationTest {

    @Test
    void answerDoesNotPersistAnExchangeWhenMemoryIsDisabled() {
        HybridMemoryManager memory = mock(HybridMemoryManager.class);
        when(memory.getDefaultUserId()).thenReturn("default-user");
        AnswerNode node = new AnswerNode(memory);

        node.apply(new OverAllState(Map.of(
                AgentGraphKeys.USER_ID, "USER1001",
                AgentGraphKeys.SESSION_ID, "admin-playground-1",
                AgentGraphKeys.QUERY, "退款规则",
                AgentGraphKeys.ANSWER, "退款规则说明",
                AgentGraphKeys.MEMORY_ENABLED, false)));

        verify(memory, never()).addExchange(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void memoryNodeUsesSuppliedHistoryWithoutAccessingPersistentMemory() {
        HybridMemoryManager memory = mock(HybridMemoryManager.class);
        UserProfileService profile = mock(UserProfileService.class);
        EmbeddingModel embedding = mock(EmbeddingModel.class);
        when(memory.getDefaultUserId()).thenReturn("default-user");
        MemoryNode node = new MemoryNode(memory, Optional.of(profile), embedding);
        List<ConversationTurn> history = List.of(
                new ConversationTurn("上一轮问题", "上一轮回答"));

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                AgentGraphKeys.USER_ID, "USER1001",
                AgentGraphKeys.SESSION_ID, "admin-playground-1",
                AgentGraphKeys.QUERY, "继续说明",
                AgentGraphKeys.HISTORY, history,
                AgentGraphKeys.MEMORY_ENABLED, false)));

        assertEquals(history, result.get(AgentGraphKeys.HISTORY));
        assertEquals(1, result.get(AgentGraphKeys.HISTORY_COUNT));
        assertEquals("", result.get(AgentGraphKeys.USER_PROFILE_CONTEXT));
        assertEquals("", result.get(AgentGraphKeys.LONG_TERM_MEMORY));
        verify(memory, never()).getRecentMessages(anyString(), anyString(), anyInt());
        verify(memory, never()).searchLongTerm(anyString(), any(float[].class), anyInt());
        verify(memory, never()).searchLongTerm(any(float[].class), anyInt());
        verify(memory, never()).formatLongTermContext(anyList());
        verify(profile, never()).formatForPrompt(anyString());
        verifyNoInteractions(embedding);
    }
}
