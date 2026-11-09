package com.css.mallorderagent.skill.model;

import java.util.Map;

public record DownloadedSkillPackage(Map<String, byte[]> files, String digest,
                                     long totalBytes, String sourceUrl,
                                     String sourceRef, String sourcePath) {
}
