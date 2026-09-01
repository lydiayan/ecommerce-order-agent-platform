package com.css.mallorderagent.planner.executor;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.Map;

/**
 * 将 {@link NodeAction} 适配为 {@link ActionExecutor}。
 */
public final class NodeActionExecutorAdapter implements ActionExecutor {

    private final NodeAction delegate;

    public NodeActionExecutorAdapter(NodeAction delegate) {
        this.delegate = delegate;
    }

    /**
     * 执行底层 Graph 节点，并将受检异常统一包装为执行失败异常。
     *
     * @param state 当前 Graph 全局状态
     * @return 节点产生的状态增量
     */
    @Override
    public Map<String, Object> execute(OverAllState state) {
        try {
            return delegate.apply(state);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Action execution failed: " + e.getMessage(), e);
        }
    }
}
