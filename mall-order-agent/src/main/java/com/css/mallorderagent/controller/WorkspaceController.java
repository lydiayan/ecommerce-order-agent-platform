package com.css.mallorderagent.controller;

import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.demo.DemoWorkspace;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.example.mallordermilvusrag.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/workspace")
public class WorkspaceController {

    private final DemoPersonaService identityService;

    public WorkspaceController(DemoPersonaService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/me")
    public ApiResponse<DemoWorkspace> current(
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ApiResponse.success(identityService.getWorkspace(principal.actorUserId()));
    }
}
