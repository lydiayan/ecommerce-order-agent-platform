package com.example.mallordermilvusrag.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTraceAttributesTest {

    @Test
    void shouldExtractPromptShapeWithoutContent() {
        Prompt prompt = new Prompt(
                new SystemMessage("你是企业知识库助手。"),
                new UserMessage("参考资料：婚假3天\n\n用户问题：婚假有多少天？")
        );

        Map<String, Object> attrs = PromptTraceAttributes.fromPrompt(prompt);

        assertEquals(2, attrs.get("messageCount"));
        assertEquals("你是企业知识库助手。".length(), attrs.get("systemPromptLength"));
        assertTrue((Integer) attrs.get("userPromptLength") > 0);
        assertFalse(attrs.containsKey("prompt"));
        assertFalse(attrs.containsKey("userPrompt"));
    }
}
