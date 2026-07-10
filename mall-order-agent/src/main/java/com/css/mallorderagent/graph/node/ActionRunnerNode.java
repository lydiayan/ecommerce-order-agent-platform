package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.ActionDefinition;
import com.css.mallorderagent.planner.ActionType;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.planner.executor.ActionExecutorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 动作调度节点：按 Planner 输出的 {@link ActionDefinition} 顺序，通过 Registry 动态执行。
 * <p>
 * {@link ActionType#LLM} 动作由后续 Graph 节点（prompt → llm）处理，此处跳过。
 * </p>
 */
@Component
public class ActionRunnerNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(ActionRunnerNode.class);

    public static final String NODE_NAME = "actionRunner";

    private final ActionExecutorRegistry actionExecutorRegistry;

    public ActionRunnerNode(ActionExecutorRegistry actionExecutorRegistry) {
        this.actionExecutorRegistry = actionExecutorRegistry;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        PlanResult plan = state.value(AgentGraphKeys.PLAN, PlanResult.class)
                .orElseThrow(() -> new IllegalStateException("plan is required before ActionRunnerNode"));

        if (plan.actions().isEmpty()) {
            log.info("ActionRunnerNode skipped, strategy={}, no actions", plan.strategy());
            return Map.of();
        }

        Map<String, Object> context = new HashMap<>(state.data());
        Map<String, Object> accumulated = new HashMap<>();

        for (ActionDefinition action : plan.actions()) {
            if (action.type() == ActionType.LLM) {
                log.debug("ActionRunnerNode skip LLM action={}, executor={}", action.action(), action.executor());
                continue;
            }

            OverAllState stepState = new OverAllState(context);
            Map<String, Object> partial = actionExecutorRegistry.execute(action.executor(), stepState);
            if (partial != null && !partial.isEmpty()) {
                accumulated.putAll(partial);
                context = OverAllState.updateState(context, partial);
            }
            log.info("ActionRunnerNode executed action={}, type={}, executor={}",
                    action.action(), action.type(), action.executor());

            if (shouldStopAfterAction(action, partial)) {
                log.info("ActionRunnerNode short-circuited after action={}", action.action());
                break;
            }
        }

        return accumulated;
    }

    /** RAG 无命中时已写入 answer，无需继续执行后续动作 */
    private static boolean shouldStopAfterAction(ActionDefinition action, Map<String, Object> partial) {
        if (action.type() != ActionType.RAG || partial == null) {
            return false;
        }
        Object grounded = partial.get(AgentGraphKeys.GROUNDED);
        return Boolean.FALSE.equals(grounded);
    }
}
