package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.tool.SensitiveOrderOperationExecutor;
import com.css.mallorderagent.tool.SensitiveOperationResult;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
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
        Map<String, Object> startAttributes = new LinkedHashMap<>();
        String conversationId = state.value(AgentGraphKeys.SESSION_ID, "");
        if (!conversationId.isBlank()) {
            startAttributes.put("conversationId", conversationId);
        }
        startAttributes.put("planStrategy", state.value(AgentGraphKeys.PLAN_STRATEGY, ""));

        RagTraceScope trace = RagTracingAdvisor.parentScope();
        try (RagTraceScope sensitiveOperationSpan = trace.child(NODE_NAME, startAttributes)) {
            try {
                SensitiveOperationResult result = sensitiveOrderOperationExecutor.execute(state);
                sensitiveOperationSpan.attribute("resultLength", result.message().length());
                sensitiveOperationSpan.attribute("grounded", result.success());
                sensitiveOperationSpan.attribute("success", result.success());
                sensitiveOperationSpan.attribute("executionStatus", result.success() ? "SUCCEEDED" : "FAILED");
                sensitiveOperationSpan.attribute("operation", result.operation());
                if (result.orderId() != null) {
                    sensitiveOperationSpan.attribute("orderFingerprint", TracePrivacy.fingerprint(result.orderId()));
                }
                if (result.userId() != null && !result.userId().isBlank()) {
                    sensitiveOperationSpan.attribute("userFingerprint", TracePrivacy.fingerprint(result.userId()));
                }
                log.info("SensitiveOperationNode completed, success={}, resultLength={}",
                        result.success(), result.message().length());
                return Map.of(
                        AgentGraphKeys.ANSWER, result.message(),
                        AgentGraphKeys.TOOL_RESULT, result.message(),
                        AgentGraphKeys.GROUNDED, result.success());
            } catch (RuntimeException e) {
                sensitiveOperationSpan.error(e);
                throw e;
            }
        }
    }
}
