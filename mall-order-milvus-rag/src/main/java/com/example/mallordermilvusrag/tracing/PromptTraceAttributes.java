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

        int systemPromptLength = 0;
        int userPromptLength = 0;
        int promptLength = 0;
        int messageCount = 0;

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
                systemPromptLength += text.length();
            } else if (type == MessageType.USER) {
                userPromptLength += text.length();
            }
            promptLength += text.length();
            messageCount++;
        }

        attrs.put("promptLength", promptLength);
        attrs.put("messageCount", messageCount);
        attrs.put("systemPromptLength", systemPromptLength);
        attrs.put("userPromptLength", userPromptLength);
        return attrs;
    }

}
