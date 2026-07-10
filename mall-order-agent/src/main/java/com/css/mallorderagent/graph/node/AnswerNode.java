package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.example.mallordermemory.memory.HybridMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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

        hybridMemoryManager.addExchange(userId, sessionId, query, answer);

        log.info("AnswerNode completed, sessionId={}, grounded={}, strategy={}", sessionId, grounded, planStrategy);

        Map<String, Object> updates = new HashMap<>();
        updates.put(AgentGraphKeys.ANSWER, answer);
        updates.put(AgentGraphKeys.GROUNDED, grounded);
        updates.put(AgentGraphKeys.PLAN_STRATEGY, planStrategy);
        updates.put(AgentGraphKeys.USER_ID, userId);
        updates.put(AgentGraphKeys.SESSION_ID, sessionId);
        updates.put(AgentGraphKeys.QUERY, query);
        return updates;
    }
}
