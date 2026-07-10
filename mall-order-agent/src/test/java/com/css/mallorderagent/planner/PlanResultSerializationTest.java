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
        String json = objectMapper.writeValueAsString(original);
        PlanResult restored = objectMapper.readValue(json, PlanResult.class);
        assertEquals("DANGEROUS_OP", restored.strategy());
        assertEquals(3, restored.actions().size());
        assertTrue(restored.humanApprovalRequired());
        assertEquals("涉及退货/退款操作", restored.approvalReason());
    }
}
