package com.css.mallorderagent.skill.planner;

import com.css.mallorderagent.planner.ActionDefinition;
import com.css.mallorderagent.planner.ActionDefinitions;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.skill.model.ActiveSkill;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SkillPlanCompiler {

    public PlanResult compile(ActiveSkill skill) {
        List<ActionDefinition> actions = new ArrayList<>();
        actions.add(ActionDefinitions.memoryLoad());
        for (String action : skill.requiredActions()) {
            switch (action) {
                case "MEMORY_LOAD" -> { }
                case "KNOWLEDGE_SEARCH" -> actions.add(ActionDefinitions.ragKnowledgeSearch());
                case "ORDER_QUERY" -> actions.add(ActionDefinitions.orderQueryTool());
                case "REFUND_ELIGIBILITY" -> actions.add(ActionDefinitions.refundEligibilityTool());
                default -> throw new IllegalStateException("unsupported active skill action: " + action);
            }
        }
        actions.add(ActionDefinitions.llmGenerate());
        PlanResult plan = new PlanResult("SKILL_EXECUTE", actions);
        plan.setIntent("SKILL");
        plan.setIntentSource("SKILL_REGISTRY");
        plan.setIntentConfidence(1D);
        plan.setRuleMatchStatus("MATCH");
        plan.setSkillName(skill.name());
        plan.setSkillVersionId(skill.versionId());
        plan.setSkillDigest(skill.packageDigest());
        plan.setSkillInstructions(skill.instructions());
        return plan;
    }
}
