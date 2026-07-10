package com.css.mallorderagent.prompt;

import com.css.mallorderagent.memory.ConversationTurn;
import com.css.mallorderagent.planner.ActionType;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.planner.PlanResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将 Memory、RAG 上下文与用户问题组装为 LLM Prompt。
 */
@Component
public class PromptBuilder {

    public BuiltPrompt build(PlanResult plan,
                             List<ConversationTurn> history,
                             String userProfileContext,
                             String longTermMemory,
                             String context,
                             String toolResult,
                             String query,
                             String systemPrompt) {
        StringBuilder userMessage = new StringBuilder();

        if (history != null && !history.isEmpty()) {
            userMessage.append("历史对话：\n");
            for (ConversationTurn turn : history) {
                userMessage.append("用户：").append(turn.userMessage()).append('\n');
                userMessage.append("助手：").append(turn.assistantMessage()).append('\n');
            }
            userMessage.append('\n');
        }

        if (userProfileContext != null && !userProfileContext.isBlank()) {
            userMessage.append(userProfileContext.trim()).append("\n\n");
        }

        if (longTermMemory != null && !longTermMemory.isBlank()) {
            userMessage.append("长期记忆：\n").append(longTermMemory.trim()).append("\n\n");
        }

        if (plan.hasType(ActionType.TOOL) && toolResult != null && !toolResult.isBlank()) {
            userMessage.append("工具查询结果：\n").append(toolResult.trim()).append("\n\n");
        }

        if (plan.needRag() && context != null && !context.isBlank()) {
            userMessage.append("参考资料：\n").append(context.trim()).append("\n\n");
        }

        userMessage.append("用户问题：").append(query.trim()).append('\n');
        if ("DANGEROUS_ORDER_OP".equals(plan.strategy())) {
            String operation = HumanApprovalDetector.resolveOperationLabel(query);
            userMessage.append("""
                    【敏感操作-订单】用户诉求：%s。
                    工具查询结果仅作参考，禁止原样复读整段订单列表。
                    回复必须：
                    1) 首句以「您确认要为订单 {订单号} 申请%s吗？」提问（订单号取自工具结果）；
                    2) 补充 2-3 条与%s相关的规则/条件（可结合参考资料）；
                    3) 不得写「已为您办理/已完成%s」。
                    """.formatted(operation, operation, operation, operation));
        } else if (plan.humanApprovalRequired()) {
            userMessage.append("""
                    【敏感操作】用户意图涉及退货/付款/取消/删除/换货/改地址等操作。请先向用户明确确认，\
                    使用「确认xxx吗？」句式询问，不要假设用户已同意，也不要描述为已执行操作。
                    """);
        } else if ("ORDER_QUERY".equals(plan.strategy())) {
            userMessage.append("""
                    请根据工具查询结果回答用户。仅展示订单信息；不要主动发起退货/退款/换货/改地址/付款等操作，\
                    不要使用「确认退货吗？」等确认句式，除非用户明确提出了该类诉求。
                    """);
        } else {
            userMessage.append("""
                    请结合历史对话、长期记忆与参考资料回答。若资料不足以回答，请明确说明。
                    """);
        }

        return new BuiltPrompt(systemPrompt, userMessage.toString());
    }
}
