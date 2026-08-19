package com.css.mallorderagent.prompt;

import com.css.mallorderagent.planner.ActionDefinitions;
import com.css.mallorderagent.planner.PlanResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void orderPolicyQueryTreatsEligibilityToolResultAsAuthoritative() {
        BuiltPrompt prompt = promptBuilder.build(
                new PlanResult("ORDER_POLICY_QUERY", ActionDefinitions.orderPolicyQueryPipeline()),
                List.of(),
                "",
                "",
                "",
                "【退款资格权威结论】\n订单号：ORD20260810001\n资格结论：ELIGIBLE\n原因编码：[PAID_AND_NOT_SHIPPED]",
                "ORD20260810001 是否可以退款",
                "你是订单助手。");

        assertTrue(prompt.userMessage().contains("资格结论：ELIGIBLE"));
        assertTrue(prompt.userMessage().contains("不得使用参考资料、常识或模型推理修改或弱化结论"));
        assertTrue(prompt.userMessage().contains("仅在 NEED_MORE_INFO 时询问 missingFields"));
    }
}
