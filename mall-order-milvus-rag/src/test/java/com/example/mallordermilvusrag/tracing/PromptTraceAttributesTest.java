package com.example.mallordermilvusrag.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTraceAttributesTest {

    @Test
    void shouldExtractSystemUserAndFullPrompt() {
        Prompt prompt = new Prompt(
                new SystemMessage("你是企业知识库助手。"),
                new UserMessage("参考资料：婚假3天\n\n用户问题：婚假有多少天？")
        );

        Map<String, Object> attrs = PromptTraceAttributes.fromPrompt(prompt);

        assertEquals("你是企业知识库助手。", attrs.get("systemPrompt"));
        assertTrue(attrs.get("userPrompt").toString().contains("婚假有多少天"));
        assertTrue(attrs.get("prompt").toString().contains("[SYSTEM]"));
        assertTrue(attrs.get("prompt").toString().contains("[USER]"));
    }

    @Test
    void shouldTruncateLongText() {
        String longText = "a".repeat(PromptTraceAttributes.MAX_TEXT_LENGTH + 10);

        String truncated = PromptTraceAttributes.truncate(longText);

        assertTrue(truncated.endsWith("...[truncated]"));
        assertEquals(PromptTraceAttributes.MAX_TEXT_LENGTH + "...[truncated]".length(), truncated.length());
    }
}
