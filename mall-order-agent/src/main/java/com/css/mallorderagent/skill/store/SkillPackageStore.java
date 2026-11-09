package com.css.mallorderagent.skill.store;

import java.util.Map;

public interface SkillPackageStore {

    String store(String skillName, String packageDigest, Map<String, byte[]> files);
}
