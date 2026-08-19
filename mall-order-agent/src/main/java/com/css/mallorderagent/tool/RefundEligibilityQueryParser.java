package com.css.mallorderagent.tool;

import java.util.List;

final class RefundEligibilityQueryParser {

    private RefundEligibilityQueryParser() {
    }

    static RefundQueryContext parse(String query) {
        String text = query != null ? query : "";
        String reasonType = resolveReasonType(text);
        Boolean opened = containsAny(text, "已拆封", "拆开", "开封", "打开包装") ? Boolean.TRUE
                : containsAny(text, "未拆封", "没拆封") ? Boolean.FALSE : null;
        Boolean used = containsAny(text, "已使用", "用过", "使用过") ? Boolean.TRUE
                : containsAny(text, "未使用", "没用过", "没有使用") ? Boolean.FALSE : null;
        String conditionStatus = containsAny(text, "影响二次销售", "破损", "严重损坏") ? "NOT_RESALABLE"
                : containsAny(text, "商品完好", "包装完整", "不影响二次销售") ? "RESALABLE" : null;
        String description = "NO_REASON".equals(reasonType) ? null : text.trim();
        return new RefundQueryContext(reasonType, opened, used, conditionStatus, description, List.of());
    }

    private static String resolveReasonType(String text) {
        if (containsAny(text, "发错", "错发", "货不对板")) {
            return "WRONG_ITEM";
        }
        if (containsAny(text, "运输损坏", "物流损坏", "运输破损")) {
            return "SHIPPING_DAMAGE";
        }
        if (containsAny(text, "质量问题", "故障", "瑕疵", "损坏")) {
            return "QUALITY_ISSUE";
        }
        return "NO_REASON";
    }

    private static boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    record RefundQueryContext(
            String reasonType,
            Boolean customerOpened,
            Boolean customerUsed,
            String conditionStatus,
            String reasonDescription,
            List<String> evidenceUrls) {
    }
}
