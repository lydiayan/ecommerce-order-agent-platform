package com.css.mallorderagent.planner.executor;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 按 Spring Bean 名称解析并执行 {@link ActionExecutor}。
 */
@Component
public class ActionExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutorRegistry.class);

    private final ApplicationContext applicationContext;

    public ActionExecutorRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Map<String, Object> execute(String executorBeanName, OverAllState state) {
        ActionExecutor executor = resolve(executorBeanName);
        log.debug("Executing action via bean '{}'", executorBeanName);
        return executor.execute(state);
    }

    public ActionExecutor resolve(String executorBeanName) {
        Object bean = applicationContext.getBean(executorBeanName);
        if (!(bean instanceof ActionExecutor executor)) {
            throw new IllegalStateException(
                    "Bean '" + executorBeanName + "' must implement ActionExecutor, actual="
                            + bean.getClass().getName());
        }
        return executor;
    }
}
