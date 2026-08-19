package com.example.mallordermilvusrag.splitter.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagSplitterPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void acceptsDefaultConfiguration() {
        assertDoesNotThrow(new RagSplitterProperties()::afterPropertiesSet);
    }

    @Test
    void rejectsSlidingOverlapAtOrAboveWindowSize() {
        RagSplitterProperties properties = new RagSplitterProperties();
        properties.getSlidingWindow().setMaxTokens(100);
        properties.getSlidingWindow().setOverlapTokens(100);

        IllegalStateException error = assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
        assertTrue(error.getMessage().contains("sliding-window.overlap-tokens"));
    }

    @Test
    void rejectsInvalidSemanticTokenOrder() {
        RagSplitterProperties properties = new RagSplitterProperties();
        properties.getSemantic().setMinTokens(300);
        properties.getSemantic().setTargetTokens(200);

        IllegalStateException error = assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
        assertTrue(error.getMessage().contains("semantic token sizes"));
    }

    @Test
    void rejectsChildLargerThanParent() {
        RagSplitterProperties properties = new RagSplitterProperties();
        properties.getParentChild().setParentTokens(100);
        properties.getParentChild().setChildTokens(200);

        IllegalStateException error = assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
        assertTrue(error.getMessage().contains("child-tokens"));
    }

    @Test
    void rejectsRemovedFlatPropertyInsteadOfSilentlyUsingDefault() {
        contextRunner.withPropertyValues("rag.chunk.chunk-size=123")
                .run(context -> assertTrue(context.getStartupFailure() != null));
    }

    @EnableConfigurationProperties(RagSplitterProperties.class)
    static class TestConfiguration {
    }
}
