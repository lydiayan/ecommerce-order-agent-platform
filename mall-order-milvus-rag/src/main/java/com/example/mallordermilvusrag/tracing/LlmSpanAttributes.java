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
    public static Map<String, Object> buildStartAttributes(Integer queryLength,
                                                           Integer contextChunks,
                                                           String model,
                                                           Double temperature,
                                                           Integer inputLength) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (model != null && !model.isBlank()) {
            attrs.put("model", model);
        }
        if (temperature != null) {
            attrs.put("temperature", temperature);
        }
        if (queryLength != null) {
            attrs.put("queryLength", queryLength);
        }
        if (inputLength != null) {
            attrs.put("inputLength", inputLength);
        }
        if (contextChunks != null) {
            attrs.put("contextChunks", contextChunks);
        }
        return attrs;
    }

    public static Map<String, Object> fromChatResponse(ChatResponse chatResponse) {
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
            if (generation.getOutput() != null) {
                String output = generation.getOutput().getText();
                if (output != null) {
                    attrs.put("outputLength", output.length());
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
