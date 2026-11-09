package com.css.mallorderagent.skill.registry;

import com.css.mallorderagent.skill.model.ActiveSkill;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SkillCatalog {

    private final SkillRepository repository;

    public SkillCatalog(SkillRepository repository) {
        this.repository = repository;
    }

    public List<ActiveSkill> activeSkills() {
        return repository.findActive();
    }
}
