package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回答节点：将最终答案写入状态并持久化到短期记忆。
 */
@Component
public class AnswerNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(AnswerNode.class);

    public static final String NODE_NAME = "answer";

    private final HybridMemoryManager hybridMemoryManager;

    public AnswerNode(HybridMemoryManager hybridMemoryManager) {
        this.hybridMemoryManager = hybridMemoryManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userId = AgentGraphSupport.resolveUserId(state, hybridMemoryManager.getDefaultUserId());
        String sessionId = AgentGraphSupport.resolveSessionId(state);
        String query = AgentGraphSupport.resolveQuery(state);
        String answer = state.value(AgentGraphKeys.ANSWER, "");
        boolean grounded = state.value(AgentGraphKeys.GROUNDED, false);
        String planStrategy = state.value(AgentGraphKeys.PLAN_STRATEGY, "RAG_QA");

        Map<String, Object> startAttributes = new LinkedHashMap<>();
        startAttributes.put("conversationId", sessionId);
        startAttributes.put("userFingerprint", TracePrivacy.fingerprint(userId));
        startAttributes.put("planStrategy", planStrategy);
        startAttributes.put("queryLength", query.length());

        RagTraceScope trace = RagTracingAdvisor.parentScope();
        try (RagTraceScope answerSpan = trace.child(NODE_NAME, startAttributes)) {
            try {
                hybridMemoryManager.addExchange(userId, sessionId, query, answer);
                answerSpan.attribute("answerLength", answer.length());
                answerSpan.attribute("grounded", grounded);
                answerSpan.attribute("memoryPersisted", true);

                log.info("AnswerNode completed, sessionId={}, grounded={}, strategy={}",
                        sessionId, grounded, planStrategy);

                Map<String, Object> updates = new HashMap<>();
                updates.put(AgentGraphKeys.ANSWER, answer);
                updates.put(AgentGraphKeys.GROUNDED, grounded);
                updates.put(AgentGraphKeys.PLAN_STRATEGY, planStrategy);
                updates.put(AgentGraphKeys.USER_ID, userId);
                updates.put(AgentGraphKeys.SESSION_ID, sessionId);
                updates.put(AgentGraphKeys.QUERY, query);
                return updates;
            } catch (RuntimeException e) {
                answerSpan.attribute("memoryPersisted", false);
                answerSpan.error(e);
                throw e;
            }
        }
    }
}
