package com.css.mallorderagent.skill.packageinfo;

import com.css.mallorderagent.skill.model.DownloadedSkillPackage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillPackageParserTest {

    private final SkillPackageParser parser = new SkillPackageParser();

    @Test
    void parsesInstructionOnlySkillAndPlatformManifest() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("SKILL.md", bytes("""
                ---
                name: refund-explainer
                description: Explain refund eligibility and policy decisions.
                ---
                Explain the authoritative eligibility result without changing it.
                """));
        files.put(SkillPackageParser.PLATFORM_MANIFEST, bytes("""
                required_actions:
                  - REFUND_ELIGIBILITY
                trigger_keywords:
                  - refund eligibility
                  - 退款资格
                allow_implicit_invocation: false
                """));

        var manifest = parser.parse(downloaded(files));

        assertEquals("refund-explainer", manifest.name());
        assertEquals(java.util.List.of("REFUND_ELIGIBILITY"), manifest.requiredActions());
        assertEquals(java.util.List.of("REFUND ELIGIBILITY", "退款资格"), manifest.triggerKeywords());
        assertFalse(manifest.allowImplicitInvocation());
    }

    @Test
    void rejectsExecutableScripts() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("SKILL.md", bytes("""
                ---
                name: unsafe-skill
                description: Unsafe test skill.
                ---
                Run the bundled script.
                """));
        files.put("scripts/run.sh", bytes("echo unsafe"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(downloaded(files)));

        assertEquals("executable skill scripts are not supported", error.getMessage());
    }

    @Test
    void rejectsUnknownActions() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("SKILL.md", bytes("""
                ---
                name: shell-skill
                description: Attempts an unsupported action.
                ---
                Run a shell command.
                """));
        files.put(SkillPackageParser.PLATFORM_MANIFEST,
                bytes("required_actions: [SHELL_EXECUTE]\n"));

        assertThrows(IllegalArgumentException.class, () -> parser.parse(downloaded(files)));
    }

    private static DownloadedSkillPackage downloaded(Map<String, byte[]> files) {
        return new DownloadedSkillPackage(files, "a".repeat(64), 100,
                "https://github.com/example/skills", "main", "");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
