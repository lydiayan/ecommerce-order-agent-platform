package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.executor.ActionExecutor;
import com.css.mallorderagent.planner.PlanResult;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.service.RagService;
import com.example.mallordermilvusrag.tracing.RagTraceOperations;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索节点：调用 RAG 知识库检索并组装上下文。
 */
@Component("retrieveNode")
public class RetrieveNode implements NodeAction, ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetrieveNode.class);

    public static final String NODE_NAME = "retrieve";

    private final RagService ragService;
    private final RagDocumentProperties.AskProperties askProperties;

    public RetrieveNode(RagService ragService, RagDocumentProperties ragDocumentProperties) {
        this.ragService = ragService;
        this.askProperties = ragDocumentProperties.getAsk();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        AskRequest request = AgentGraphSupport.requireAskRequest(state);
        String query = AgentGraphSupport.resolveQuery(state);

        SearchResponse retrieval;
        RagTraceScope trace = RagTracingAdvisor.parentScope();
        try (RagTraceScope retrieveSpan = trace.child(RagTraceOperations.RETRIEVE)) {
            retrieval = ragService.search(AgentGraphSupport.toSearchRequest(request, askProperties, state), retrieveSpan);
            retrieveSpan.attribute("hitCount", retrieval.getTotalHits());
            retrieveSpan.attribute("reranked", retrieval.isReranked());
            retrieveSpan.attribute("evidence", buildEvidence(retrieval.getHits()));
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(AgentGraphKeys.RETRIEVAL, retrieval);

        String planStrategy = state.value(AgentGraphKeys.PLAN, PlanResult.class)
                .map(PlanResult::strategy)
                .orElse("");
        boolean continueWithOrderFacts = "DANGEROUS_ORDER_OP".equals(planStrategy)
                || "ORDER_POLICY_QUERY".equals(planStrategy);

        if (retrieval.getHits() == null || retrieval.getHits().isEmpty()) {
            log.info("RetrieveNode completed, queryLength={}, hits=0, grounded=false", query.length());
            updates.put(AgentGraphKeys.GROUNDED, false);
            updates.put(AgentGraphKeys.CONTEXT, "");
            updates.put(AgentGraphKeys.CONTEXT_HIT_COUNT, 0);
            // 已有订单工具结果时不因 RAG 未命中而丢掉实时订单事实。
            if (!continueWithOrderFacts) {
                updates.put(AgentGraphKeys.ANSWER, AgentGraphSupport.NO_CONTEXT_ANSWER);
            }
            return updates;
        }

        int contextLimit = Math.min(
                Math.max(askProperties.getContextTopK(), 1),
                retrieval.getHits().size());
        List<SearchResponse.SearchHit> contextHits = retrieval.getHits().subList(0, contextLimit);
        String context = AgentGraphSupport.buildContext(contextHits);

        updates.put(AgentGraphKeys.GROUNDED, true);
        updates.put(AgentGraphKeys.CONTEXT, context);
        updates.put(AgentGraphKeys.CONTEXT_HIT_COUNT, contextHits.size());
        log.info("RetrieveNode completed, queryLength={}, totalHits={}, contextChunks={}, grounded=true",
                query.length(), retrieval.getTotalHits(), contextHits.size());
        return updates;
    }

    @Override
    public Map<String, Object> execute(OverAllState state) {
        return apply(state);
    }

    private static List<Map<String, Object>> buildEvidence(List<SearchResponse.SearchHit> hits) {
        if (hits == null) {
            return List.of();
        }
        return hits.stream().limit(10).map(hit -> {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("chunkId", hit.getId());
            evidence.put("score", hit.getScore());
            if (hit.getMetadata() != null && hit.getMetadata().getSource() != null) {
                evidence.put("source", hit.getMetadata().getSource());
            }
            return evidence;
        }).toList();
    }
}
