package com.example.mallorderweb.threadpool;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolconfig {

    @Bean(name="mallExecutor",destroyMethod = "shutdown")
    public ThreadPoolExecutor threadPoolTaskExecutor(MeterRegistry meterRegistry) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0,
                17,
                60,
                TimeUnit.MINUTES,
                new SynchronousQueue<Runnable>(),
                new NamedThreadFactory("mcp-client-thread-pool"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        // 注册监控指标
        ExecutorServiceMetrics.monitor(meterRegistry, executor, "custom-thread-pool");

        return executor;
    }

}
