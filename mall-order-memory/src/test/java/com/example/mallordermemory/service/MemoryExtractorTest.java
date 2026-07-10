package com.example.mallordermemory.service;

import com.example.mallordermemory.memory.MemoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryExtractorTest {

    @Test
    void unwrapJsonStripsMarkdownFence() {
        String raw = """
                ```json
                {"memories": []}
                ```
                """;
        assertEquals("{\"memories\": []}", MemoryExtractor.unwrapJson(raw));
    }

    @Test
    void unwrapJsonExtractsEmbeddedObject() {
        String raw = "说明文字 {\"memories\": []} 结尾";
        assertEquals("{\"memories\": []}", MemoryExtractor.unwrapJson(raw));
    }

    @Test
    void memoryTypeParseSupportsMultipleFormats() {
        assertEquals(MemoryType.USER_PROFILE, MemoryType.parse("USER_PROFILE"));
        assertEquals(MemoryType.USER_PROFILE, MemoryType.parse("user_profile"));
        assertEquals(MemoryType.USER_PROFILE, MemoryType.parse("用户画像"));
        assertEquals(MemoryType.FACT, MemoryType.parse("fact"));
        assertEquals(MemoryType.SUMMARY, MemoryType.parse("摘要"));
    }
}
