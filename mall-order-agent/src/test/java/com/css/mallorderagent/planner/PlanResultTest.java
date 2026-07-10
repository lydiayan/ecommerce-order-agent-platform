package com.css.mallorderagent.planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanResultTest {

    @Test
    void ragPipelineNeedRag() {
        PlanResult plan = new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline());
        assertTrue(plan.needRag());
        assertTrue(plan.needLlm());
        assertTrue(plan.needMemory());
        assertTrue(plan.hasAction(ActionDefinitions.KNOWLEDGE_SEARCH));
    }

    @Test
    void orderQueryPipelineHasTool() {
        PlanResult plan = new PlanResult("ORDER_QUERY", ActionDefinitions.orderQueryPipeline());
        assertFalse(plan.needRag());
        assertTrue(plan.hasType(ActionType.TOOL));
        assertTrue(plan.hasAction(ActionDefinitions.ORDER_QUERY));
    }
}
