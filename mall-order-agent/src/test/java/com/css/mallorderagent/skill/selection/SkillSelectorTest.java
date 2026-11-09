package com.css.mallorderagent.skill.selection;

import com.css.mallorderagent.skill.model.ActiveSkill;
import com.css.mallorderagent.skill.registry.SkillCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillSelectorTest {

    private final SkillCatalog catalog = mock(SkillCatalog.class);
    private final SkillSelector selector = new SkillSelector(catalog);

    @Test
    void explicitInvocationSelectsSkillEvenWhenImplicitInvocationIsDisabled() {
        when(catalog.activeSkills()).thenReturn(List.of(skill("refund-explainer", false, List.of())));

        var selected = selector.select("请用 $refund-explainer 解释这个结论");

        assertEquals("refund-explainer", selected.orElseThrow().name());
    }

    @Test
    void implicitInvocationUsesDeclaredTriggerKeywords() {
        when(catalog.activeSkills()).thenReturn(List.of(
                skill("refund-explainer", true, List.of("退款资格"))));

        var selected = selector.select("请解释我的退款资格");

        assertEquals("refund-explainer", selected.orElseThrow().name());
    }

    @Test
    void equalImplicitScoresDoNotChooseArbitrarily() {
        when(catalog.activeSkills()).thenReturn(List.of(
                skill("first-skill", true, List.of("退款")),
                skill("second-skill", true, List.of("退款"))));

        assertTrue(selector.select("解释退款").isEmpty());
    }

    private static ActiveSkill skill(String name, boolean implicit, List<String> keywords) {
        return new ActiveSkill(1, 2, name, "A focused skill description.", "a".repeat(64),
                "Follow the workflow.", List.of(), keywords, implicit);
    }
}
