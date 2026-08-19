package com.css.mallorderagent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefundEligibilityQueryParserTest {

    @Test
    void extractsQualityReasonAndConditionDeclarations() {
        RefundEligibilityQueryParser.RefundQueryContext context = RefundEligibilityQueryParser.parse(
                "ORD20260810001 有质量问题，商品未使用、包装完整，可以退款吗");

        assertEquals("QUALITY_ISSUE", context.reasonType());
        assertEquals(false, context.customerUsed());
        assertEquals("RESALABLE", context.conditionStatus());
    }

    @Test
    void genericEligibilityQuestionDefaultsToNoReason() {
        RefundEligibilityQueryParser.RefundQueryContext context = RefundEligibilityQueryParser.parse(
                "ORD20260810001 是否可以退款");

        assertEquals("NO_REASON", context.reasonType());
    }
}
