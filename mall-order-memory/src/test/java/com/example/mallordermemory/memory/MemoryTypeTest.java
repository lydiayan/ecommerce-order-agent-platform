package com.example.mallordermemory.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MemoryTypeTest {

    @Test
    void shouldResolveCollectionName() {
        assertEquals("memory_user_profile", MemoryType.USER_PROFILE.collectionName());
        assertEquals("memory_fact", MemoryType.FACT.collectionName());
        assertEquals("memory_summary", MemoryType.SUMMARY.collectionName());
    }

    @Test
    void shouldResolveFromCode() {
        assertEquals(MemoryType.FACT, MemoryType.fromCode("fact"));
    }

    @Test
    void parseSupportsEnumCodeAndChineseAliases() {
        assertEquals(MemoryType.USER_PROFILE, MemoryType.parse("USER_PROFILE"));
        assertEquals(MemoryType.USER_PROFILE, MemoryType.parse("user_profile"));
        assertEquals(MemoryType.USER_PROFILE, MemoryType.parse("用户画像"));
        assertEquals(MemoryType.SUMMARY, MemoryType.parse("摘要"));
    }

    @Test
    void shouldStoreEmbeddingAsPrimitiveArray() {
        MemoryEntry entry = new MemoryEntry("id1", MemoryType.FACT, "退货7天内", "conv", "user");
        entry.setEmbedding(new float[]{0.1f, 0.2f});
        assertNotNull(entry.getEmbedding());
        assertEquals(2, entry.getEmbedding().length);
    }
}
