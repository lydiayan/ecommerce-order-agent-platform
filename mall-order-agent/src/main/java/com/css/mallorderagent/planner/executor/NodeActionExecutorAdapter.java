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
