package com.css.mallorderagent.skill.packageinfo;

import com.css.mallorderagent.skill.model.DownloadedSkillPackage;
import com.css.mallorderagent.skill.model.SkillManifest;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SkillPackageParser {

    public static final String PLATFORM_MANIFEST = "agents/ecommerce-order-agent.yaml";
    private static final int MAX_INSTRUCTION_CHARACTERS = 32_000;
    private static final int MAX_TRIGGER_KEYWORDS = 25;
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "MEMORY_LOAD", "KNOWLEDGE_SEARCH", "ORDER_QUERY", "REFUND_ELIGIBILITY");

    public SkillManifest parse(DownloadedSkillPackage skillPackage) {
        rejectExecutableScripts(skillPackage.files());
        String skillMarkdown = utf8(skillPackage.files().get("SKILL.md"), "SKILL.md");
        FrontMatter frontMatter = parseFrontMatter(skillMarkdown);
        String name = requiredString(frontMatter.metadata(), "name").toLowerCase();
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("skill name must match " + NAME.pattern());
        }
        String description = requiredString(frontMatter.metadata(), "description");
        if (description.length() > 1000) {
            throw new IllegalArgumentException("skill description exceeds 1000 characters");
        }
        String instructions = frontMatter.body().trim();
        if (instructions.isBlank()) {
            throw new IllegalArgumentException("SKILL.md instructions must not be blank");
        }
        if (instructions.length() > MAX_INSTRUCTION_CHARACTERS) {
            throw new IllegalArgumentException("SKILL.md instructions exceed context safety limit");
        }

        Map<String, Object> platform = parseOptionalYaml(skillPackage.files().get(PLATFORM_MANIFEST));
        List<String> actions = normalizedList(platform.get("required_actions"));
        for (String action : actions) {
            if (!SUPPORTED_ACTIONS.contains(action)) {
                throw new IllegalArgumentException("unsupported skill action: " + action);
            }
        }
        long toolActions = actions.stream()
                .filter(action -> "ORDER_QUERY".equals(action) || "REFUND_ELIGIBILITY".equals(action))
                .count();
        if (toolActions > 1) {
            throw new IllegalArgumentException("a skill may declare at most one order tool action");
        }
        List<String> triggerKeywords = normalizedList(platform.get("trigger_keywords"));
        if (triggerKeywords.size() > MAX_TRIGGER_KEYWORDS
                || triggerKeywords.stream().mapToInt(String::length).sum() > 1500) {
            throw new IllegalArgumentException("skill declares too many trigger keywords");
        }
        boolean allowImplicit = booleanValue(platform.get("allow_implicit_invocation"), true);
        return new SkillManifest(name, description, instructions,
                List.copyOf(actions), List.copyOf(triggerKeywords), allowImplicit);
    }

    private static FrontMatter parseFrontMatter(String markdown) {
        String normalized = markdown.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException("SKILL.md must start with YAML front matter");
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException("SKILL.md front matter is not closed");
        }
        String yaml = normalized.substring(4, end);
        Object loaded = newYaml().load(yaml);
        if (!(loaded instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("SKILL.md front matter must be a mapping");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) raw;
        return new FrontMatter(metadata, normalized.substring(end + 5));
    }

    private static Map<String, Object> parseOptionalYaml(byte[] content) {
        if (content == null) {
            return Map.of();
        }
        Object loaded = newYaml().load(utf8(content, PLATFORM_MANIFEST));
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(PLATFORM_MANIFEST + " must be a mapping");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) raw;
        return result;
    }

    private static Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(10);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(200_000);
        return new Yaml(new SafeConstructor(options));
    }

    private static List<String> normalizedList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("skill manifest list value has invalid type");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object item : values) {
            if (!(item instanceof String string) || string.isBlank()) {
                throw new IllegalArgumentException("skill manifest list values must be non-blank strings");
            }
            String normalizedItem = string.trim();
            if (normalizedItem.length() > 100) {
                throw new IllegalArgumentException("skill manifest value exceeds 100 characters");
            }
            normalized.add(normalizedItem.toUpperCase());
        }
        return new ArrayList<>(normalized);
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException("allow_implicit_invocation must be boolean");
        }
        return bool;
    }

    private static String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("SKILL.md front matter requires " + key);
        }
        return string.trim();
    }

    private static String utf8(byte[] content, String name) {
        if (content == null) {
            throw new IllegalArgumentException("skill package is missing " + name);
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private static void rejectExecutableScripts(Map<String, byte[]> files) {
        if (files.keySet().stream().anyMatch(path -> path.equals("scripts") || path.startsWith("scripts/"))) {
            throw new IllegalArgumentException("executable skill scripts are not supported");
        }
    }

    private record FrontMatter(Map<String, Object> metadata, String body) {
    }
}
