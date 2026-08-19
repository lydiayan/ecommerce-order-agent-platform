package com.example.mallordermilvusrag.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmSpanAttributesTest {

    @Test
    void shouldExtractModelTokensAndFinishReason() {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("qwen-plus")
                .usage(new DefaultUsage(120, 36))
                .build();
        Generation generation = new Generation(
                new AssistantMessage("婚假为3个工作日。"),
                ChatGenerationMetadata.builder().finishReason("stop").build()
        );
        ChatResponse chatResponse = new ChatResponse(List.of(generation), metadata);

        Map<String, Object> attrs = LlmSpanAttributes.fromChatResponse(chatResponse);

        assertEquals("qwen-plus", attrs.get("model"));
        assertEquals(120, attrs.get("inputToken"));
        assertEquals(36, attrs.get("outputToken"));
        assertEquals("stop", attrs.get("finishReason"));
        assertEquals(9, attrs.get("outputLength"));
        assertFalse(attrs.containsKey("output"));
    }

    @Test
    void shouldReturnEmptyMapForNullResponse() {
        assertTrue(LlmSpanAttributes.fromChatResponse(null).isEmpty());
    }

    @Test
    void shouldBuildStartAttributes() {
        Map<String, Object> attrs = LlmSpanAttributes.buildStartAttributes(
                3, 2, "qwen-plus", 0.3, 25);

        assertEquals("qwen-plus", attrs.get("model"));
        assertEquals(0.3, attrs.get("temperature"));
        assertEquals(2, attrs.get("contextChunks"));
        assertEquals(3, attrs.get("queryLength"));
        assertEquals(25, attrs.get("inputLength"));
        assertFalse(attrs.containsKey("input"));
        assertFalse(attrs.containsKey("userQuery"));
    }
}
