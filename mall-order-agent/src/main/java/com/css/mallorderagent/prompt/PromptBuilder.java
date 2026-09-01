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

    /**
     * 按规划策略组合历史对话、用户画像、长期记忆、RAG 证据和工具结果。
     *
     * @param plan Planner 输出，决定允许写入 Prompt 的上下文和安全约束
     * @param history 当前会话的历史问答
     * @param userProfileContext 用户动态画像上下文
     * @param longTermMemory 与问题相关的长期记忆
     * @param context RAG 检索证据
     * @param toolResult 订单或资格工具返回结果
     * @param query 当前用户问题
     * @param systemPrompt 系统级角色和行为约束
     * @return 分离后的系统 Prompt 与用户消息
     */
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
            userMessage.append("身份与表达上下文（仅用于称谓和回答深度，不作为权限依据）：\n")
                    .append(userProfileContext.trim()).append("\n\n");
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
        } else if ("ORDER_POLICY_QUERY".equals(plan.strategy())) {
            userMessage.append("""
                    工具查询结果是订单规则服务计算出的退款资格权威结论。回复必须：
                    1) 严格保持工具中的资格结论，不得使用参考资料、常识或模型推理修改或弱化结论；
                    2) ELIGIBLE 表示可以提交申请，不承诺最终退款成功；MANUAL_REVIEW 表示需要人工处理；
                    3) 仅在 NEED_MORE_INFO 时询问 missingFields，并且只询问其中列出的字段；
                    4) 不得声称工具已提供的字段缺失，不得声称系统无法实时获取订单状态；
                    5) 结合 reasonCodes 和 nextAction 用简洁中文解释，不要在本次资格查询中直接创建退款工单。
                    """);
        } else if ("ORDER_QUERY".equals(plan.strategy())) {
            userMessage.append("""
                    请根据工具查询结果回答用户。仅展示订单信息；不要主动发起退货/退款/换货/改地址/付款等操作，\
                    不要使用「确认退货吗？」等确认句式，除非用户明确提出了该类诉求。
                    """);
        } else if ("RAG_QA".equals(plan.strategy())) {
            userMessage.append("""
                    【企业知识问答】请仅结合当前身份获准访问的参考资料回答。
                    身份只影响资料范围和表达方式，不改变问题意图，也不能用于推断未提供的事实。
                    若资料不足以回答，请明确说明，不要编造或扩大访问范围。
                    """);
        } else {
            userMessage.append("""
                    请结合历史对话、长期记忆、工具结果与参考资料回答。若信息不足，请明确说明。
                    """);
        }

        return new BuiltPrompt(systemPrompt, userMessage.toString());
    }
}
