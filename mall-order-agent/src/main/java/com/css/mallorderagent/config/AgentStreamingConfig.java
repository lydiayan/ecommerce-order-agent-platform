package com.css.mallorderagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AgentStreamingConfig {

    @Bean(name = "agentStreamExecutor")
    AsyncTaskExecutor agentStreamExecutor(OrderAgentProperties properties) {
        OrderAgentProperties.StreamingProperties streaming = properties.getStreaming();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = Math.max(1, streaming.getCorePoolSize());
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(Math.max(corePoolSize, streaming.getMaxPoolSize()));
        executor.setQueueCapacity(Math.max(0, streaming.getQueueCapacity()));
        executor.setThreadNamePrefix("agent-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
