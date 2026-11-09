package com.css.mallorderagent.skill.model;

import java.time.LocalDateTime;

public record SkillView(long id, String name, String description, boolean enabled,
                        Long activeVersionId, boolean allowImplicitInvocation,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
