package com.css.mallorderagent.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanResultSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void planResultRoundTrip() throws Exception {
        PlanResult original = new PlanResult("DANGEROUS_OP", ActionDefinitions.ragQaPipeline(), true, "涉及退货/退款操作");
        original.setIntent(IntentType.SENSITIVE_ORDER_OPERATION.name());
        original.setIntentSource(IntentSource.RULE.name());
        original.setIntentConfidence(1D);
        original.setRuleMatchStatus(RuleMatchStatus.MATCH.name());
        String json = objectMapper.writeValueAsString(original);
        PlanResult restored = objectMapper.readValue(json, PlanResult.class);
        assertEquals("DANGEROUS_OP", restored.strategy());
        assertEquals(3, restored.actions().size());
        assertTrue(restored.humanApprovalRequired());
        assertEquals("涉及退货/退款操作", restored.approvalReason());
        assertEquals("SENSITIVE_ORDER_OPERATION", restored.intent());
        assertEquals("RULE", restored.intentSource());
        assertEquals(1D, restored.intentConfidence());
        assertEquals("MATCH", restored.ruleMatchStatus());
    }
}
