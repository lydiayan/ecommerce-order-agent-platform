package com.css.mallorderagent.demo;

import java.util.List;

public record DemoPersonaView(
        String actorUserId,
        DemoPersonaCategory category,
        String displayName,
        String jobTitle,
        String department,
        String description,
        String welcomeMessage,
        List<String> roleScopes,
        List<String> departmentScopes,
        List<DemoCapability> capabilities,
        List<String> suggestions) {
}
