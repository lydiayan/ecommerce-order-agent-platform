package com.css.mallorderagent.planner;

/** 一次意图分类的可观测结果，不包含用户原始问题。 */
public record IntentClassification(
        IntentType intent,
        IntentSource source,
        RuleMatchStatus ruleMatchStatus,
        double confidence,
        boolean clarificationRequired,
        String fallbackReason) {

    public IntentClassification {
        intent = intent != null ? intent : IntentType.UNKNOWN;
        source = source != null ? source : IntentSource.FALLBACK;
        ruleMatchStatus = ruleMatchStatus != null ? ruleMatchStatus : RuleMatchStatus.NO_MATCH;
        confidence = Math.max(0D, Math.min(confidence, 1D));
    }
}
