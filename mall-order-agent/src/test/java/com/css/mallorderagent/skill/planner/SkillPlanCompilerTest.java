package com.css.mallorderagent.skill.planner;

import com.css.mallorderagent.planner.ActionDefinitions;
import com.css.mallorderagent.skill.model.ActiveSkill;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillPlanCompilerTest {

    @Test
    void mapsOnlyAllowlistedLogicalActionsToExistingExecutors() {
        ActiveSkill skill = new ActiveSkill(1, 9, "refund-explainer", "desc", "b".repeat(64),
                "Explain the result.", List.of("REFUND_ELIGIBILITY"), List.of("退款资格"), true);

        var plan = new SkillPlanCompiler().compile(skill);

        assertEquals("SKILL_EXECUTE", plan.strategy());
        assertEquals(List.of(ActionDefinitions.MEMORY_LOAD, ActionDefinitions.REFUND_ELIGIBILITY,
                        ActionDefinitions.LLM_GENERATE),
                plan.actions().stream().map(action -> action.action()).toList());
        assertEquals("refund-explainer", plan.skillName());
        assertEquals(9L, plan.skillVersionId());
    }
}
