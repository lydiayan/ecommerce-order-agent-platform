package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.tool.SensitiveOrderOperationExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 人工审核通过后执行敏感订单 Tool，并将结果写入 answer。
 */
@Component
public class SensitiveOperationNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(SensitiveOperationNode.class);

    public static final String NODE_NAME = "sensitiveOp";

    private final SensitiveOrderOperationExecutor sensitiveOrderOperationExecutor;

    public SensitiveOperationNode(SensitiveOrderOperationExecutor sensitiveOrderOperationExecutor) {
        this.sensitiveOrderOperationExecutor = sensitiveOrderOperationExecutor;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String result = sensitiveOrderOperationExecutor.execute(state);
        log.info("SensitiveOperationNode completed, resultLength={}", result.length());
        return Map.of(
                AgentGraphKeys.ANSWER, result,
                AgentGraphKeys.TOOL_RESULT, result,
                AgentGraphKeys.GROUNDED, true);
    }
}
