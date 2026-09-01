package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.memory.ConversationTurn;
import com.css.mallorderagent.planner.executor.ActionExecutor;
import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallordermemory.memory.MemoryEntry;
import com.example.mallordermemory.service.UserProfileService;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 记忆节点：加载 Redis 短期历史、MySQL 用户画像与 Milvus 长期记忆。
 */
@Component("memoryNode")
public class MemoryNode implements NodeAction, ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(MemoryNode.class);

    public static final String NODE_NAME = "memory";

    private static final int HISTORY_MESSAGE_LIMIT = 20;
    private static final int LONG_TERM_MEMORY_TOP_K = 5;

    private final HybridMemoryManager hybridMemoryManager;
    private final Optional<UserProfileService> userProfileService;
    private final EmbeddingModel embeddingModel;

    public MemoryNode(HybridMemoryManager hybridMemoryManager,
                      Optional<UserProfileService> userProfileService,
                      EmbeddingModel embeddingModel) {
        this.hybridMemoryManager = hybridMemoryManager;
        this.userProfileService = userProfileService;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 在独立 Trace Span 中加载短期历史、长期记忆和用户画像。
     *
     * @param state 包含用户、会话和问题的 Graph 状态
     * @return 历史对话、长期记忆、画像及对应计数
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        String userId = AgentGraphSupport.resolveUserId(state, hybridMemoryManager.getDefaultUserId());
        String sessionId = AgentGraphSupport.resolveSessionId(state);

        Map<String, Object> startAttributes = new LinkedHashMap<>();
        startAttributes.put("conversationId", sessionId);
        startAttributes.put("userFingerprint", TracePrivacy.fingerprint(userId));
        startAttributes.put("queryLength", query.length());

        RagTraceScope trace = RagTracingAdvisor.parentScope();
        try (RagTraceScope memorySpan = trace.child(NODE_NAME, startAttributes)) {
            try {
                List<ConversationTurn> history = loadHistory(userId, sessionId);
                String userProfileContext = loadUserProfileContext(userId);
                LongTermMemory longTermMemory = loadLongTermMemory(userId, query);
                boolean profileLoaded = !userProfileContext.isBlank();
                int memoryCount = longTermMemory.count() + (profileLoaded ? 1 : 0);

                memorySpan.attribute("historyCount", history.size());
                memorySpan.attribute("longTermMemoryCount", longTermMemory.count());
                memorySpan.attribute("longTermMemoryRetrievalSucceeded", longTermMemory.retrievalSucceeded());
                memorySpan.attribute("profileLoaded", profileLoaded);
                memorySpan.attribute("memoryCount", memoryCount);

                log.info("MemoryNode completed, history={}, longTerm={}, profile={}",
                        history.size(), longTermMemory.count(), profileLoaded);

                Map<String, Object> updates = new HashMap<>();
                updates.put(AgentGraphKeys.USER_ID, userId);
                updates.put(AgentGraphKeys.SESSION_ID, sessionId);
                updates.put(AgentGraphKeys.QUERY, query);
                updates.put(AgentGraphKeys.HISTORY, history);
                updates.put(AgentGraphKeys.HISTORY_COUNT, history.size());
                updates.put(AgentGraphKeys.USER_PROFILE_CONTEXT, userProfileContext);
                updates.put(AgentGraphKeys.LONG_TERM_MEMORY, longTermMemory.context());
                updates.put(AgentGraphKeys.MEMORY_COUNT, memoryCount);
                return updates;
            } catch (RuntimeException e) {
                memorySpan.error(e);
                throw e;
            }
        }
    }

    /**
     * 执行实际的混合记忆读取，供 Graph 节点和动态动作调度复用。
     *
     * @param state 包含用户、会话和问题的 Graph 状态
     * @return 可直接合并进 Graph 的记忆状态增量
     */
    @Override
    public Map<String, Object> execute(OverAllState state) {
        return apply(state);
    }

    private List<ConversationTurn> loadHistory(String userId, String sessionId) {
        List<Message> messages = hybridMemoryManager.getRecentMessages(userId, sessionId, HISTORY_MESSAGE_LIMIT);
        return AgentGraphSupport.toConversationTurns(messages);
    }

    private String loadUserProfileContext(String userId) {
        return userProfileService.map(service -> service.formatForPrompt(userId)).orElse("");
    }

    private LongTermMemory loadLongTermMemory(String userId, String query) {
        try {
            float[] queryEmbedding = embeddingModel.embed(query);
            List<MemoryEntry> entries = hybridMemoryManager.searchLongTerm(
                    userId, queryEmbedding, LONG_TERM_MEMORY_TOP_K);
            return new LongTermMemory(
                    entries.size(),
                    hybridMemoryManager.formatLongTermContext(entries),
                    true);
        } catch (Exception e) {
            log.debug("Skip long-term memory retrieval: {}", e.getMessage());
            return LongTermMemory.empty();
        }
    }

    private record LongTermMemory(int count, String context, boolean retrievalSucceeded) {
        private static LongTermMemory empty() {
            return new LongTermMemory(0, "", false);
        }
    }
}
