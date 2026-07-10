package com.example.mallordermilvusrag.tracing;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuildSpanAttributesTest {

    @Test
    void shouldBuildPromptBuildAttributes() {
        Map<String, Object> attrs = PromptBuildSpanAttributes.build(
                "v1", "你是助手", "用户消息", 3, 0, 0);

        assertEquals("v1", attrs.get("promptVersion"));
        assertEquals(3, attrs.get("chunkCount"));
        assertEquals(0, attrs.get("historyCount"));
        assertEquals(0, attrs.get("memoryCount"));
        assertEquals("你是助手".length() + "用户消息".length(), attrs.get("promptLength"));
        assertEquals("你是助手", attrs.get("systemPrompt"));
    }
}
