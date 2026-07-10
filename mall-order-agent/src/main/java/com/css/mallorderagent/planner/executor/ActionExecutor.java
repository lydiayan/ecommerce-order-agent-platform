package com.css.mallorderagent.planner.executor;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.planner.ActionDefinition;

import java.util.Map;

/**
 * 规划动作执行器：由 {@link ActionExecutorRegistry} 按 {@link ActionDefinition#executor()} 调度。
 */
public interface ActionExecutor {

    Map<String, Object> execute(OverAllState state);
}
