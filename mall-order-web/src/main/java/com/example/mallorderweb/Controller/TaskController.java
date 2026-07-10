package com.example.mallorderweb.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RestController
public class TaskController {

    private final ThreadPoolExecutor mallExecutor;

    public TaskController(ThreadPoolExecutor mallExecutor) {
        this.mallExecutor = mallExecutor;
    }

    /**
     * 提交任务到线程池
     * 示例：
     *   GET /submit?tasks=20&sleepMs=2000
     *
     * @param tasks 任务数量
     * @param sleepMs 每个任务睡眠时间（毫秒）
     * @return 提交结果
     */
    @GetMapping("/submit")
    public String submitTasks(@RequestParam(defaultValue = "10") int tasks,
                              @RequestParam(defaultValue = "1000") long sleepMs) {

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < tasks; i++) {
            futures.add(mallExecutor.submit(() -> {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignored) {
                }
            }));
        }

        // 可选：快速确认任务已提交
        futures.forEach(f -> {
            try {
                f.get(50, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
        });
        System.out.printf("submitted tasks");
        return String.format("Submitted %d tasks, each sleeping %d ms", tasks, sleepMs);
    }
}