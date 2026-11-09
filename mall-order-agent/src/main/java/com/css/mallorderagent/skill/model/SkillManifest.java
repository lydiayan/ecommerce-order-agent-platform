package com.css.mallorderagent.skill.model;

import java.util.List;

public record SkillManifest(String name, String description, String instructions,
                            List<String> requiredActions, List<String> triggerKeywords,
                            boolean allowImplicitInvocation) {
}
