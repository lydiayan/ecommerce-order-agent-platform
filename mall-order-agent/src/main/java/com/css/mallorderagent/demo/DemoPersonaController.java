package com.css.mallorderagent.demo;

import com.example.mallordermilvusrag.dto.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("demo")
@RequestMapping("/agent/demo")
public class DemoPersonaController {

    private final DemoPersonaService demoPersonaService;
    private final DemoResetService demoResetService;

    public DemoPersonaController(DemoPersonaService demoPersonaService, DemoResetService demoResetService) {
        this.demoPersonaService = demoPersonaService;
        this.demoResetService = demoResetService;
    }

    /**
     * 列出 demo profile 中预置的全部演示身份。
     *
     * @return 可供演示和管理员分配的身份列表
     */
    @GetMapping("/personas")
    public ApiResponse<List<DemoPersonaView>> personas() {
        return ApiResponse.success(demoPersonaService.findAll());
    }

    /**
     * 查询指定演示身份的工作台、能力和授权数据范围。
     *
     * @param actorUserId 演示业务身份编号
     * @return 该身份对应的工作台数据
     */
    @GetMapping("/personas/{actorUserId}/workspace")
    public ApiResponse<DemoWorkspace> workspace(@PathVariable("actorUserId") String actorUserId) {
        return ApiResponse.success(demoPersonaService.getWorkspace(actorUserId));
    }

    /**
     * 重置 Agent 演示环境中的订单、售后、会话记忆、动态画像和待确认状态。
     *
     * @return 各项演示数据的重置结果
     */
    @PostMapping("/reset")
    public ApiResponse<DemoResetService.DemoResetResult> reset() {
        return ApiResponse.success(demoResetService.reset());
    }
}
