package com.css.mallorderagent.skill.model;

import java.time.LocalDateTime;
import java.util.List;

public record SkillVersionView(long id, long skillId, String packageDigest,
                               String sourceUrl, String sourceRef, String sourcePath,
                               String storageKey, List<String> requiredActions,
                               List<String> triggerKeywords, String createdBy,
                               LocalDateTime createdAt) {
}
