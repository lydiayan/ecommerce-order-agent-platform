package com.example.mallordermemory.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenEstimatorTest {

    @Test
    void estimateChineseText() {
        int tokens = TokenEstimator.estimate("婚假几天");
        assertTrue(tokens >= 2 && tokens <= 6);
    }

    @Test
    void estimateMixedText() {
        int tokens = TokenEstimator.estimate("Order 订单 status 12345");
        assertTrue(tokens > 0);
    }

    @Test
    void estimateBlankReturnsZero() {
        assertEquals(0, TokenEstimator.estimate(""));
        assertEquals(0, TokenEstimator.estimate((String) null));
    }
}
