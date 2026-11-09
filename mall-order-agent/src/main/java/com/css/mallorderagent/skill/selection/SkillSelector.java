package com.css.mallorderagent.skill.selection;

import com.css.mallorderagent.skill.model.ActiveSkill;
import com.css.mallorderagent.skill.registry.SkillCatalog;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SkillSelector {

    private static final Pattern EXPLICIT = Pattern.compile("(?<![\\p{L}\\p{N}_-])\\$([a-zA-Z0-9][a-zA-Z0-9_-]{0,63})");
    private static final Pattern DESCRIPTION_TOKEN = Pattern.compile("[\\p{L}\\p{N}_-]{2,}");

    private final SkillCatalog catalog;

    public SkillSelector(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    public Optional<ActiveSkill> select(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        List<ActiveSkill> active = catalog.activeSkills();
        Matcher explicit = EXPLICIT.matcher(query);
        if (explicit.find()) {
            String name = explicit.group(1);
            return active.stream().filter(skill -> skill.name().equalsIgnoreCase(name)).findFirst();
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        List<ScoredSkill> matches = active.stream()
                .filter(ActiveSkill::allowImplicitInvocation)
                .map(skill -> new ScoredSkill(skill, score(skill, normalized)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredSkill::score).reversed()
                        .thenComparing(scored -> scored.skill().name()))
                .toList();
        if (matches.isEmpty() || (matches.size() > 1 && matches.get(0).score() == matches.get(1).score())) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0).skill());
    }

    private static int score(ActiveSkill skill, String query) {
        int score = query.contains(skill.name().toLowerCase(Locale.ROOT)) ? 20 : 0;
        for (String keyword : skill.triggerKeywords()) {
            if (query.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 10;
            }
        }
        Matcher matcher = DESCRIPTION_TOKEN.matcher(skill.description().toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (query.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private record ScoredSkill(ActiveSkill skill, int score) {
    }
}
