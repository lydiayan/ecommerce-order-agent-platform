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

    /**
     * 在 demo profile 下将订单和售后数据恢复为预置演示状态。
     *
     * @return 各类订单演示数据的重置数量和结果
     */
    @PostMapping("/reset")
    public DemoOrderResetService.DemoOrderResetResult reset() {
        return resetService.reset();
    }
}
