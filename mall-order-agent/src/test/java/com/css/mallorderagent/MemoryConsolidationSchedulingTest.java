package com.css.mallorderagent;

import com.example.mallordermemory.config.MemoryProperties;
import com.example.mallordermemory.service.MemoryConsolidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "memory.consolidation.interval-ms=1000",
        "memory.user-profile.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class MemoryConsolidationSchedulingLiveIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void memoryConsolidationServiceBeanExists() {
        assertNotNull(applicationContext.getBean(MemoryConsolidationService.class));
        assertNotNull(applicationContext.getBean("memoryConsolidationProperties", MemoryProperties.ConsolidationProperties.class));
    }

    @Test
    void scheduledConsolidationTaskIsRegistered() {
        ScheduledAnnotationBeanPostProcessor processor =
                applicationContext.getBean(ScheduledAnnotationBeanPostProcessor.class);
        Set<ScheduledTask> scheduledTasks = processor.getScheduledTasks();

        boolean hasConsolidationTask = scheduledTasks.stream()
                .anyMatch(task -> task.toString().contains("scheduledConsolidation")
                        || task.toString().contains("MemoryConsolidationService"));

        assertTrue(hasConsolidationTask, "scheduledConsolidation task not registered, tasks=" + scheduledTasks);
    }
}
