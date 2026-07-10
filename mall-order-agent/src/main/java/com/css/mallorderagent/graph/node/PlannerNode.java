package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.planner.Planner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 规划节点：根据用户问题决定策略（如是否走 RAG、调用哪些工具）。
 */
@Component
public class PlannerNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);

    public static final String NODE_NAME = "planner";

    private final Planner planner;

    public PlannerNode(Planner planner) {
        this.planner = planner;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        PlanResult plan = planner.plan(query);

        log.info("PlannerNode completed, strategy={}, actions={}, humanApproval={}",
                plan.strategy(), plan.actions(), plan.humanApprovalRequired());

        return Map.of(
                AgentGraphKeys.PLAN, plan,
                AgentGraphKeys.PLAN_STRATEGY, plan.strategy(),
                AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, plan.humanApprovalRequired(),
                AgentGraphKeys.APPROVAL_REASON, plan.approvalReason() != null ? plan.approvalReason() : "");
    }
}
