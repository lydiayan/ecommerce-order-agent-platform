package com.example.mallordermilvusrag.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RagDocumentPropertiesTest {

    @Autowired
    private RagDocumentProperties properties;

    @Test
    void shouldBindRagConfigFromRagYml() {
        assertEquals("data", properties.getDataDir());
        assertEquals(250, properties.getChunk().getChunkSize());
        assertEquals(80, properties.getChunk().getMinChunkSizeChars());
        assertTrue(properties.getRerank().isEnabled());
        assertEquals("qwen3-rerank", properties.getRerank().getModel());
        assertEquals("qwen-plus", properties.getAsk().getModel());
        assertEquals(5, properties.getAsk().getContextTopK());
        assertEquals(7, properties.getCatalog().size(), "catalog list size");
        assertTrue(properties.catalogByFilename().containsKey("01_HR员工手册.pdf"),
                "catalog keys: " + properties.catalogByFilename().keySet());

        RagDocumentProperties.CatalogEntry entry =
                properties.catalogByFilename().get("01_HR员工手册.pdf");
        assertNotNull(entry);
        assertEquals("HR", entry.getDepartment());
        assertEquals("hr", entry.getRole());
        assertEquals("3.2", entry.getVersion());
    }
}
