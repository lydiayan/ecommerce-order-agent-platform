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

    /**
     * 获取当前业务身份对应的工作台信息，包括角色、能力和可访问客户范围。
     *
     * @param principal 当前登录身份，用于确定实际业务身份
     * @return 当前身份的演示工作台数据
     */
    @GetMapping("/me")
    public ApiResponse<DemoWorkspace> current(
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ApiResponse.success(identityService.getWorkspace(principal.actorUserId()));
    }
}
