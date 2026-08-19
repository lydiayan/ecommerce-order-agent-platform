package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.ActionDefinitions;
import com.css.mallorderagent.planner.PlanResult;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.SearchRequest;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.service.RagService;
import com.example.mallorderobservability.trace.RagTraceScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrieveNodeTest {

    @Test
    void orderPolicyQueryKeepsOrderFactsWhenKnowledgeIsMissing() {
        Map<String, Object> result = retrieveWithNoHits(
                new PlanResult("ORDER_POLICY_QUERY", ActionDefinitions.orderPolicyQueryPipeline()));

        assertEquals(false, result.get(AgentGraphKeys.GROUNDED));
        assertEquals("", result.get(AgentGraphKeys.CONTEXT));
        assertFalse(result.containsKey(AgentGraphKeys.ANSWER));
    }

    @Test
    void regularRagQueryStillReturnsNoContextAnswerWhenKnowledgeIsMissing() {
        Map<String, Object> result = retrieveWithNoHits(
                new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline()));

        assertEquals(false, result.get(AgentGraphKeys.GROUNDED));
        assertEquals(AgentGraphSupport.NO_CONTEXT_ANSWER, result.get(AgentGraphKeys.ANSWER));
    }

    private static Map<String, Object> retrieveWithNoHits(PlanResult plan) {
        RagService ragService = mock(RagService.class);
        when(ragService.search(any(SearchRequest.class), any(RagTraceScope.class)))
                .thenReturn(new SearchResponse("退款", 0, List.of()));

        AskRequest request = new AskRequest();
        request.setQuery("ORD20260810001 是否可以退款");
        RetrieveNode node = new RetrieveNode(ragService, new RagDocumentProperties());
        return node.apply(new OverAllState(Map.of(
                AgentGraphKeys.ASK_REQUEST, request,
                AgentGraphKeys.QUERY, request.getQuery(),
                AgentGraphKeys.PLAN, plan)));
    }
}
