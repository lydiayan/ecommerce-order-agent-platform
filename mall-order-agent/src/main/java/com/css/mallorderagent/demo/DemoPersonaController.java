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

    @GetMapping("/personas")
    public ApiResponse<List<DemoPersonaView>> personas() {
        return ApiResponse.success(demoPersonaService.findAll());
    }

    @GetMapping("/personas/{actorUserId}/workspace")
    public ApiResponse<DemoWorkspace> workspace(@PathVariable("actorUserId") String actorUserId) {
        return ApiResponse.success(demoPersonaService.getWorkspace(actorUserId));
    }

    @PostMapping("/reset")
    public ApiResponse<DemoResetService.DemoResetResult> reset() {
        return ApiResponse.success(demoResetService.reset());
    }
}
