package com.example.mallorder.controller;

import com.example.mallorder.service.DemoOrderResetService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo")
@RequestMapping("/internal/demo")
public class DemoOrderResetController {

    private final DemoOrderResetService resetService;

    public DemoOrderResetController(DemoOrderResetService resetService) {
        this.resetService = resetService;
    }

    @PostMapping("/reset")
    public DemoOrderResetService.DemoOrderResetResult reset() {
        return resetService.reset();
    }
}
