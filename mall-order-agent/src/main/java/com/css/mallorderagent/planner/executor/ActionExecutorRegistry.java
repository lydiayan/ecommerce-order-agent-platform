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

    /**
     * 按 Bean 名称解析动作执行器并执行当前 Graph 状态。
     *
     * @param executorBeanName Planner 动作定义中的 Spring Bean 名称
     * @param state 当前 Graph 状态
     * @return 执行器产生的状态增量
     */
    public Map<String, Object> execute(String executorBeanName, OverAllState state) {
        ActionExecutor executor = resolve(executorBeanName);
        log.debug("Executing action via bean '{}'", executorBeanName);
        return executor.execute(state);
    }

    /**
     * 从 Spring 容器解析并校验动作执行器类型。
     *
     * @param executorBeanName Spring Bean 名称
     * @return 实现 ActionExecutor 的 Bean
     * @throws IllegalStateException Bean 未实现 ActionExecutor 时抛出
     */
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
