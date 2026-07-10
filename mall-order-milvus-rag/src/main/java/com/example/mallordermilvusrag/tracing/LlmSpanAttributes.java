package com.example.mallordermilvusrag.tracing;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从 Spring AI {@link ChatResponse} 提取 LLM span 指标。
 */
public final class LlmSpanAttributes {

    private LlmSpanAttributes() {
    }

    /**
     * llm 单条 SPAN_END 使用的轻量起始属性。
     */
    static Map<String, Object> buildStartAttributes(String userQuery,
                                                    Integer contextChunks,
                                                    String model,
                                                    Double temperature,
                                                    String input) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (model != null && !model.isBlank()) {
            attrs.put("model", model);
        }
        if (temperature != null) {
            attrs.put("temperature", temperature);
        }
        putText(attrs, "userQuery", userQuery);
        putText(attrs, "input", input);
        if (contextChunks != null) {
            attrs.put("contextChunks", contextChunks);
        }
        return attrs;
    }

    private static void putText(Map<String, Object> attrs, String key, String value) {
        if (value != null && !value.isBlank()) {
            attrs.put(key, PromptTraceAttributes.truncate(value));
        }
    }

    static Map<String, Object> fromChatResponse(ChatResponse chatResponse) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (chatResponse == null) {
            return attrs;
        }

        ChatResponseMetadata metadata = chatResponse.getMetadata();
        if (metadata != null) {
            if (metadata.getModel() != null && !metadata.getModel().isBlank()) {
                attrs.put("model", metadata.getModel());
            }
            Usage usage = metadata.getUsage();
            if (usage != null) {
                putIfPresent(attrs, "inputToken", usage.getPromptTokens());
                putIfPresent(attrs, "outputToken", usage.getCompletionTokens());
            }
        }

        Generation generation = chatResponse.getResult();
        if (generation != null) {
            ChatGenerationMetadata generationMetadata = generation.getMetadata();
            if (generationMetadata != null) {
                String finishReason = generationMetadata.getFinishReason();
                if (finishReason != null && !finishReason.isBlank()) {
                    attrs.put("finishReason", finishReason);
                }
            }
        }
        return attrs;
    }

    private static void putIfPresent(Map<String, Object> attrs, String key, Integer value) {
        if (value != null) {
            attrs.put(key, value);
        }
    }
}
