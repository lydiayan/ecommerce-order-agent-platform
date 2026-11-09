package com.css.mallorderagent.skill.model;

import java.util.List;

public record ActiveSkill(long skillId, long versionId, String name, String description,
                          String packageDigest, String instructions,
                          List<String> requiredActions, List<String> triggerKeywords,
                          boolean allowImplicitInvocation) {
}
