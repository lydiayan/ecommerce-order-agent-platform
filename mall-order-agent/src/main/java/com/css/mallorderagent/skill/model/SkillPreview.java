package com.css.mallorderagent.skill.model;

import java.util.List;

public record SkillPreview(String name, String description, String instructions, String packageDigest,
                           List<String> requiredActions, List<String> triggerKeywords,
                           boolean allowImplicitInvocation, List<String> fileNames,
                           int fileCount, long totalBytes) {
}
