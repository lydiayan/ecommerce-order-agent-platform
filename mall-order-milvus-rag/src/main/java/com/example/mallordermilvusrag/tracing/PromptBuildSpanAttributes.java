package com.example.mallordermilvusrag.tracing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * prompt_build span 属性。
 */
public final class PromptBuildSpanAttributes {

    private PromptBuildSpanAttributes() {
    }

    public static Map<String, Object> build(String promptVersion,
                                            String systemPrompt,
                                            String userMessage,
                                            int chunkCount,
                                            int historyCount,
                                            int memoryCount) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (promptVersion != null && !promptVersion.isBlank()) {
            attrs.put("promptVersion", promptVersion);
        }
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            attrs.put("systemPrompt", PromptTraceAttributes.truncate(systemPrompt));
        }
        int promptLength = (systemPrompt != null ? systemPrompt.length() : 0)
                + (userMessage != null ? userMessage.length() : 0);
        attrs.put("promptLength", promptLength);
        attrs.put("chunkCount", chunkCount);
        attrs.put("historyCount", historyCount);
        attrs.put("memoryCount", memoryCount);
        return attrs;
    }
}
