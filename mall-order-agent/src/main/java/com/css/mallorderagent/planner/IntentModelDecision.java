package com.css.mallorderagent.planner;

/** 轻量模型返回的受限分类结果。 */
public record IntentModelDecision(
        IntentType intent,
        double confidence,
        boolean clarificationRequired,
        String reasonCode) {

    public IntentModelDecision {
        intent = intent != null ? intent : IntentType.UNKNOWN;
        confidence = Math.max(0D, Math.min(confidence, 1D));
    }

    public static IntentModelDecision unknown(String reasonCode) {
        return new IntentModelDecision(IntentType.UNKNOWN, 0D, true, reasonCode);
    }
}
