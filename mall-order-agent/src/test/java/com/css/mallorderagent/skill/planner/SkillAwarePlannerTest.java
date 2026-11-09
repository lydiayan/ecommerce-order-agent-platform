package com.css.mallorderagent.skill.planner;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.planner.DefaultPlanner;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.skill.model.ActiveSkill;
import com.css.mallorderagent.skill.selection.SkillSelector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillAwarePlannerTest {

    private final DefaultPlanner fallback = mock(DefaultPlanner.class);
    private final SkillSelector selector = mock(SkillSelector.class);
    private final SkillPlanCompiler compiler = mock(SkillPlanCompiler.class);
    private final OrderAgentProperties properties = new OrderAgentProperties();
    private final SkillAwarePlanner planner = new SkillAwarePlanner(fallback, selector, compiler, properties);

    @Test
    void selectedSkillTakesPrecedenceOverBuiltInPlanner() {
        ActiveSkill skill = new ActiveSkill(1, 2, "refund-explainer", "desc", "a".repeat(64),
                "instructions", List.of(), List.of(), true);
        PlanResult skillPlan = new PlanResult("SKILL_EXECUTE", List.of());
        when(selector.select("$refund-explainer explain")).thenReturn(Optional.of(skill));
        when(compiler.compile(skill)).thenReturn(skillPlan);

        assertEquals(skillPlan, planner.plan("$refund-explainer explain"));
        verify(compiler).compile(skill);
    }

    @Test
    void noMatchPreservesExistingPlannerBehavior() {
        PlanResult fallbackPlan = new PlanResult("RAG_QA", List.of());
        when(selector.select("退款规则是什么")).thenReturn(Optional.empty());
        when(fallback.plan("退款规则是什么")).thenReturn(fallbackPlan);

        assertEquals(fallbackPlan, planner.plan("退款规则是什么"));
        verify(fallback).plan("退款规则是什么");
    }
}
