package com.css.mallorderagent.demo;

import java.util.List;

public record DemoActorContext(
        String actorUserId,
        String personaPrompt,
        List<DemoCapability> capabilities,
        List<String> authorizedCustomerIds,
        List<String> roleScopes,
        List<String> departmentScopes) {
}
