package com.css.mallorderagent.skill.source;

import com.css.mallorderagent.config.OrderAgentProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitArchiveSkillSourceClientTest {

    @Test
    void selectsRequestedSkillDirectoryWithoutRepositoryPrefix() {
        Map<String, byte[]> archive = new LinkedHashMap<>();
        archive.put("skills-main/README.md", bytes("root"));
        archive.put("skills-main/skills/refund/SKILL.md", bytes("skill"));
        archive.put("skills-main/skills/refund/references/rules.md", bytes("rules"));
        archive.put("skills-main/skills/other/SKILL.md", bytes("other"));

        Map<String, byte[]> selected = GitArchiveSkillSourceClient.selectSkillFiles(
                archive, "skills/refund");

        assertEquals(List.of("SKILL.md", "references/rules.md"), selected.keySet().stream().toList());
    }

    @Test
    void rejectsAmbiguousRootSkills() {
        Map<String, byte[]> archive = Map.of(
                "repo/one/SKILL.md", bytes("one"),
                "repo/two/SKILL.md", bytes("two"));

        assertThrows(IllegalArgumentException.class,
                () -> GitArchiveSkillSourceClient.selectSkillFiles(archive, ""));
    }

    @Test
    void sourcePolicyRejectsPrivateAddressesAndCredentials() {
        OrderAgentProperties properties = new OrderAgentProperties();
        properties.getSkill().setAllowedHosts(List.of("127.0.0.1"));
        SkillSourcePolicy policy = new SkillSourcePolicy(properties);

        assertThrows(IllegalArgumentException.class,
                () -> policy.validate(URI.create("https://127.0.0.1/skill.zip")));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validate(URI.create("https://token@127.0.0.1/skill.zip")));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
