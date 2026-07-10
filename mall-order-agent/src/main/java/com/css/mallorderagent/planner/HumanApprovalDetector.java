package com.css.mallorderagent.planner;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 判断当前问答是否涉及敏感操作、需要人工审核（Human-in-the-Loop）。
 * <p>
 * 普通咨询（如「退款规则是什么」）不审核；执行类/确认类操作（如「我要退货」「确认付款吗」）才审核。
 * </p>
 */
public final class HumanApprovalDetector {

    private static final List<String> INFORMATIONAL_MARKERS = List.of(
            "规则", "政策", "流程", "时效", "是什么", "什么是", "怎么回事",
            "怎么", "如何", "能不能", "可以吗", "有哪些", "多少", "介绍", "说明",
            "查询", "查一下", "查看", "进度", "状态", "到账", "到哪", "什么时候", "多久");

    private static final List<String> ACTION_INTENT_MARKERS = List.of(
            "我要", "帮我", "请帮", "确认", "申请", "立即", "马上", "去付", "去支付");

    private static final List<String> STANDALONE_SENSITIVE_TERMS = List.of(
            "退货", "退款", "换货", "改地址", "取消订单", "取消", "付款", "支付");

    private static final List<String> DANGEROUS_QUERY_MARKERS = List.of(
            "申请退货", "申请退款", "我要退", "帮我退", "确认退货", "确认退款",
            "确认付款", "确认支付", "取消订单", "删除订单", "删除账号",
            "去付款", "去支付", "立即支付", "立即付款", "跳转付款", "马上付",
            "申请换货", "我要换货", "帮我换货", "确认换货",
            "改地址", "修改地址", "变更地址", "更换地址", "确认改地址", "修改收货地址", "改收货地址");

    private static final Pattern CONFIRM_QUESTION =
            Pattern.compile("确认.{0,25}(退|退款|退货|付|支付|付款|取消|删除|换货|地址).{0,15}吗[？?]?");
    private static final Pattern CONFIRM_SHORT =
            Pattern.compile("确认(?:申请)?(?:退|退款|退货|换货|付|支付|付款|改地址|取消).{0,6}吗[？?]?");
    private static final Pattern RISK_CONFIRM =
            Pattern.compile("(确定|是否).{0,12}(删除订单|取消.{0,3}订单|申请退|确认退|确认付|换货|收货地址|修改地址)");
    private static final Pattern ORDER_ID = Pattern.compile("ORD\\w+");
    private static final Pattern PRODUCT_LINE = Pattern.compile("-\\s*(.+?)\\s+x\\d+");

    /** 用户对确认话术的回复意图。 */
    public enum ConfirmationIntent {
        CONFIRM, CANCEL, UNKNOWN
    }

    private HumanApprovalDetector() {
    }

    /** 从用户问题解析敏感操作名称，用于生成确认话术。 */
    public static String resolveOperationLabel(String query) {
        if (query == null || query.isBlank()) {
            return "该操作";
        }
        String text = query.trim();
        if (text.contains("退款")) {
            return "退款";
        }
        if (text.contains("退货")) {
            return "退货";
        }
        if (text.contains("换货")) {
            return "换货";
        }
        if (containsAny(text, "地址", "收货地址")) {
            return "修改收货地址";
        }
        if (text.contains("取消")) {
            return "取消订单";
        }
        if (text.contains("删除")) {
            return "删除";
        }
        if (containsAny(text, "付", "支付", "付款")) {
            return "付款";
        }
        if (text.contains("退")) {
            return "退款";
        }
        return "该操作";
    }

    /** 构造敏感订单操作的确认话术（不依赖 LLM，避免复读订单列表）。 */
    public static String buildDangerousOrderConfirmation(String query, String toolResult, String ragContext) {
        String operation = resolveOperationLabel(query);
        String orderId = extractFirstOrderId(toolResult).orElse("上述订单");
        String productName = extractFirstProductName(toolResult).orElse(null);
        StringBuilder sb = new StringBuilder();
        if (productName != null && !productName.isBlank()) {
            if ("退货".equals(operation)) {
                sb.append("您确定要退订单号 ").append(orderId).append(" 的").append(productName).append("吗？");
            } else {
                sb.append("您确定要为订单号 ").append(orderId).append(" 的").append(productName)
                        .append("申请").append(operation).append("吗？");
            }
        } else if ("退货".equals(operation)) {
            sb.append("您确定要退订单号 ").append(orderId).append(" 的商品吗？");
        } else {
            sb.append("您确定要为订单 ").append(orderId).append(" 申请").append(operation).append("吗？");
        }
        sb.append("\n\n请回复「确认」执行操作，或回复「取消」放弃。");
        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("\n\n相关规则：\n").append(trimContext(ragContext));
        }
        return sb.toString().trim();
    }

    /** 解析用户对确认话术的回复。 */
    public static ConfirmationIntent parseUserConfirmationIntent(String text) {
        if (text == null || text.isBlank()) {
            return ConfirmationIntent.UNKNOWN;
        }
        String normalized = text.trim().replaceAll("[？?。！!，,、\\s]", "");
        if (isCancelReply(normalized)) {
            return ConfirmationIntent.CANCEL;
        }
        if (isConfirmReply(normalized)) {
            return ConfirmationIntent.CONFIRM;
        }
        return ConfirmationIntent.UNKNOWN;
    }

    public static String buildCancelMessage(String query) {
        return "好的，已为您取消" + resolveOperationLabel(query) + "申请。";
    }

    /** @deprecated 使用 {@link #buildDangerousOrderConfirmation} */
    public static String buildConfirmationFallback(String query, String toolResult) {
        return buildDangerousOrderConfirmation(query, toolResult, null);
    }

    public static boolean isDangerousOrderOp(String planStrategy) {
        return "DANGEROUS_ORDER_OP".equals(planStrategy);
    }

    private static String trimContext(String ragContext) {
        String trimmed = ragContext.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500) + "...";
    }

    public static Optional<String> extractFirstOrderId(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = ORDER_ID.matcher(toolResult);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
    }

    public static Optional<String> extractFirstProductName(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PRODUCT_LINE.matcher(toolResult);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).trim());
        }
        return Optional.empty();
    }

    private static boolean isConfirmReply(String normalized) {
        if (normalized.equals("确认") || normalized.equals("确定") || normalized.equals("同意")
                || normalized.equals("是的") || normalized.equals("是") || normalized.equals("好的")
                || normalized.equals("可以") || normalized.equals("执行") || normalized.equals("确认执行")) {
            return true;
        }
        return normalized.endsWith("确认") || normalized.endsWith("确定")
                || normalized.contains("确认执行") || normalized.contains("确定执行");
    }

    private static boolean isCancelReply(String normalized) {
        if (normalized.equals("取消") || normalized.equals("不要") || normalized.equals("算了")
                || normalized.equals("不用") || normalized.equals("否") || normalized.equals("不")
                || normalized.equals("放弃") || normalized.equals("不确认")) {
            return true;
        }
        return normalized.contains("取消") || normalized.contains("不要了") || normalized.contains("算了");
    }

    public static boolean queryRequiresApproval(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String text = query.trim();
        if (isInformationalOnly(text)) {
            return false;
        }
        if (isQueryOrStatusIntent(text)) {
            return false;
        }
        if (isStandaloneSensitiveIntent(text)) {
            return true;
        }
        return DANGEROUS_QUERY_MARKERS.stream().anyMatch(text::contains);
    }

    /** 退货/退款/换货等场景通常需要附带订单上下文。 */
    public static boolean shouldAttachOrderContext(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String text = query.trim();
        if (text.contains("订单")) {
            return true;
        }
        return containsAny(text, "退货", "退款", "换货", "取消订单", "取消");
    }

    public static boolean answerRequiresApproval(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String text = answer.trim();
        return CONFIRM_QUESTION.matcher(text).find()
                || CONFIRM_SHORT.matcher(text).find()
                || RISK_CONFIRM.matcher(text).find();
    }

    public static boolean requiresApproval(String query, String answer) {
        return queryRequiresApproval(query) || answerRequiresApproval(answer);
    }

    /** 结合全局开关与 Planner 标记，判断当前轮次是否进入人工审核。 */
    public static boolean requiresReview(boolean globalEnabled,
                                         boolean planRequires,
                                         String query,
                                         String answer) {
        if (!globalEnabled) {
            return false;
        }
        return planRequires || queryRequiresApproval(query) || answerRequiresApproval(answer);
    }

    public static String resolveReason(String query, String answer) {
        if (queryRequiresApproval(query)) {
            return resolveQueryReason(query);
        }
        if (answerRequiresApproval(answer)) {
            return "助手回复包含敏感操作确认，请人工审核后再发送给用户";
        }
        return "请审核助手回答";
    }

    private static String resolveQueryReason(String query) {
        String text = query.trim();
        if (containsAny(text, "退", "退款", "退货")) {
            return "涉及退货/退款操作，请审核助手回复后再发送";
        }
        if (containsAny(text, "付", "支付", "付款")) {
            return "涉及付款/支付操作，请审核助手回复后再发送";
        }
        if (text.contains("取消")) {
            return "涉及取消订单操作，请审核助手回复后再发送";
        }
        if (text.contains("删除")) {
            return "涉及删除操作，请审核助手回复后再发送";
        }
        if (text.contains("换货")) {
            return "涉及换货操作，请审核助手回复后再发送";
        }
        if (containsAny(text, "地址", "收货地址")) {
            return "涉及修改收货地址操作，请审核助手回复后再发送";
        }
        return "涉及敏感操作，请审核助手回复后再发送";
    }

    private static boolean isInformationalOnly(String text) {
        boolean hasInfoMarker = INFORMATIONAL_MARKERS.stream().anyMatch(text::contains);
        boolean hasExplicitAction = ACTION_INTENT_MARKERS.stream().anyMatch(text::contains);
        return hasInfoMarker && !hasExplicitAction;
    }

    /**
     * 查询类/进度类咨询，如「查询退款进度」「退款到账了吗」，非执行敏感操作。
     */
    private static boolean isQueryOrStatusIntent(String text) {
        if (hasActionIntentMarkers(text)) {
            return false;
        }
        boolean hasQueryIntent = containsAny(text, "查询", "查一下", "查查", "查看", "看看", "了解", "询问");
        boolean hasStatusIntent = containsAny(text, "进度", "状态", "到账", "到哪", "什么时候", "多久",
                "何时", "完成了吗", "处理了吗", "办好了吗", "到了吗");
        boolean hasAfterSalesTopic = containsAny(text, "退", "退款", "退货", "换货", "取消", "售后", "物流");
        if (hasStatusIntent && hasAfterSalesTopic) {
            return true;
        }
        if (hasQueryIntent && hasStatusIntent) {
            return true;
        }
        return hasQueryIntent && hasAfterSalesTopic;
    }

    private static boolean hasActionIntentMarkers(String text) {
        return ACTION_INTENT_MARKERS.stream().anyMatch(text::contains)
                || DANGEROUS_QUERY_MARKERS.stream().anyMatch(text::contains);
    }

    /** 单词或短句表达的敏感操作意图，如「退货」「退款」。 */
    private static boolean isStandaloneSensitiveIntent(String text) {
        if (isQueryOrStatusIntent(text)) {
            return false;
        }
        String normalized = text.replaceAll("[？?。！!，,、\\s]", "");
        if (STANDALONE_SENSITIVE_TERMS.contains(normalized)) {
            return true;
        }
        if (normalized.length() <= 16) {
            return STANDALONE_SENSITIVE_TERMS.stream().anyMatch(normalized::contains);
        }
        return false;
    }

    private static boolean containsAny(String text, String... parts) {
        for (String part : parts) {
            if (text.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
