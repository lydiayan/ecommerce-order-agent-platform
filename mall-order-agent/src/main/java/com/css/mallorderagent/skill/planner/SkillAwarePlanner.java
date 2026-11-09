package com.css.mallorderagent.skill.planner;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.planner.DefaultPlanner;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.planner.Planner;
import com.css.mallorderagent.skill.selection.SkillSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SkillAwarePlanner implements Planner {

    private static final Logger log = LoggerFactory.getLogger(SkillAwarePlanner.class);

    private final DefaultPlanner fallback;
    private final SkillSelector selector;
    private final SkillPlanCompiler compiler;
    private final OrderAgentProperties.SkillProperties properties;

    public SkillAwarePlanner(DefaultPlanner fallback, SkillSelector selector,
                             SkillPlanCompiler compiler, OrderAgentProperties properties) {
        this.fallback = fallback;
        this.selector = selector;
        this.compiler = compiler;
        this.properties = properties.getSkill();
    }

    @Override
    public PlanResult plan(String question) {
        if (properties.isEnabled()) {
            try {
                var selected = selector.select(question);
                if (selected.isPresent()) {
                    return compiler.compile(selected.get());
                }
            } catch (RuntimeException e) {
                log.warn("Skill selection failed; falling back to the built-in planner", e);
            }
        }
        return fallback.plan(question);
    }
}
