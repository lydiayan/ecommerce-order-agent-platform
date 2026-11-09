package com.css.mallorderagent.skill.model;

public record SkillSourceRequest(String sourceUrl, String ref, String path, String expectedDigest) {

    public String normalizedRef() {
        return ref != null && !ref.isBlank() ? ref.trim() : "main";
    }

    public String normalizedPath() {
        return path != null ? path.trim() : "";
    }
}
