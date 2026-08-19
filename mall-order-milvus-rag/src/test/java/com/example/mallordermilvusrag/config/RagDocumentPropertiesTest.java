package com.example.mallordermilvusrag.config;

import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagDocumentPropertiesTest {

    @Test
    void shouldBindRagConfigFromRagYml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("rag-test", new ClassPathResource("rag.yml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        assertEquals("127.0.0.1",
                environment.getProperty("spring.ai.vectorstore.milvus.client.host"));
        assertEquals(29530,
                environment.getProperty("spring.ai.vectorstore.milvus.client.port", Integer.class));

        RagDocumentProperties properties = Binder.get(environment)
                .bind("rag", Bindable.of(RagDocumentProperties.class))
                .orElseThrow(() -> new IllegalStateException("rag.yml did not bind"));

        assertEquals("data", properties.getDataDir());
        assertEquals("mall_rag_v3", properties.getCollectionName());
        assertEquals(1536, properties.getDimensions());

        RagSplitterProperties splitter = Binder.get(environment)
                .bind("rag.chunk", Bindable.of(RagSplitterProperties.class))
                .orElseThrow(() -> new IllegalStateException("rag.chunk did not bind"));
        assertEquals(RagSplitStrategy.CONTENT_TYPE_AWARE, splitter.getStrategy());
        assertEquals(10000, splitter.getMaxNumChunks());
        assertEquals(250, splitter.getFixedSize().getMaxTokens());
        assertEquals(250, splitter.getSlidingWindow().getMaxTokens());
        assertEquals(50, splitter.getSlidingWindow().getOverlapTokens());
        assertEquals(80, splitter.getRecursive().getMinTokens());
        assertEquals(400, splitter.getRecursive().getMaxTokens());
        assertEquals(250, splitter.getSemantic().getTargetTokens());
        assertEquals(800, splitter.getParentChild().getParentTokens());
        assertTrue(properties.getRerank().isEnabled());
        assertEquals("qwen3-rerank", properties.getRerank().getModel());
        assertEquals("qwen-plus", properties.getAsk().getModel());
        assertEquals(5, properties.getAsk().getContextTopK());
        assertEquals(8, properties.getCatalog().size(), "catalog list size");
        assertTrue(properties.catalogByFilename().containsKey("01_HR员工手册.pdf"),
                "catalog keys: " + properties.catalogByFilename().keySet());

        RagDocumentProperties.CatalogEntry entry =
                properties.catalogByFilename().get("01_HR员工手册.pdf");
        assertNotNull(entry);
        assertEquals("HR", entry.getDepartment());
        assertEquals("hr", entry.getRole());
        assertEquals("3.2", entry.getVersion());

        RagDocumentProperties.CatalogEntry sales =
                properties.catalogByFilename().get("08_销售业务手册.pdf");
        assertNotNull(sales);
        assertEquals("Sales", sales.getDepartment());
        assertEquals("sales", sales.getRole());
    }
}
