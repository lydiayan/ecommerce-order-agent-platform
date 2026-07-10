package com.example.mallordermilvusrag.tracing;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Spring AI {@link Prompt} 提取可写入 trace 的属性。
 */
public final class PromptTraceAttributes {

    static final int MAX_TEXT_LENGTH = 8000;

    private PromptTraceAttributes() {
    }

    static Map<String, Object> fromPrompt(Prompt prompt) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (prompt == null) {
            return attrs;
        }

        List<Message> messages = prompt.getInstructions();
        if (messages == null || messages.isEmpty()) {
            return attrs;
        }

        String systemPrompt = null;
        String userPrompt = null;
        StringBuilder fullPrompt = new StringBuilder();

        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            MessageType type = message.getMessageType();
            if (type == MessageType.SYSTEM) {
                systemPrompt = text;
            } else if (type == MessageType.USER) {
                userPrompt = text;
            }

            if (fullPrompt.length() > 0) {
                fullPrompt.append('\n');
            }
            fullPrompt.append('[').append(type.name()).append("] ").append(text);
        }

        putIfPresent(attrs, "systemPrompt", systemPrompt);
        putIfPresent(attrs, "userPrompt", userPrompt);
        putIfPresent(attrs, "prompt", fullPrompt.toString());
        return attrs;
    }

    static String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH) + "...[truncated]";
    }

    private static void putIfPresent(Map<String, Object> attrs, String key, String value) {
        if (value != null && !value.isBlank()) {
            attrs.put(key, truncate(value));
        }
    }
}
