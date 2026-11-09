package com.css.mallorderagent.skill.source;

import com.css.mallorderagent.skill.model.DownloadedSkillPackage;
import com.css.mallorderagent.skill.model.SkillSourceRequest;

public interface SkillSourceClient {

    DownloadedSkillPackage download(SkillSourceRequest request);
}
