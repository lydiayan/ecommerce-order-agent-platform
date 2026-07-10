package com.example.mallordermemory.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisShortTermMemoryStoreTest {

    @Test
    void normalizePrefixAddsTrailingColon() {
        assertEquals("memory:short:", RedisShortTermMemoryStore.normalizePrefix("memory:short"));
        assertEquals("memory:short:", RedisShortTermMemoryStore.normalizePrefix("memory:short:"));
    }
}
