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

    @Test
    void enterpriseKnowledgePromptKeepsIdentityContextSeparateFromAuthorization() {
        BuiltPrompt prompt = promptBuilder.build(
                new PlanResult("RAG_QA", ActionDefinitions.ragQaPipeline()),
                List.of(),
                "当前认证身份：周航（后端工程师，Engineering）。",
                "",
                "[1] 来源：06_技术开发规范.pdf\n代码评审应覆盖可读性、测试和安全边界。",
                "",
                "代码评审有哪些要求？",
                "你是企业知识库助手。");

        assertTrue(prompt.userMessage().contains("身份与表达上下文（仅用于称谓和回答深度，不作为权限依据）"));
        assertTrue(prompt.userMessage().contains("【企业知识问答】请仅结合当前身份获准访问的参考资料回答"));
        assertTrue(prompt.userMessage().contains("身份只影响资料范围和表达方式，不改变问题意图"));
    }
}
