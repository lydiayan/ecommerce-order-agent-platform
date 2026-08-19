package com.example.mallordermilvusrag.config;

import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagPropertiesAutoConfiguration.class));

    @Test
    void registersAndBindsRagPropertiesForEmbeddingApplications() {
        contextRunner.withPropertyValues(
                        "rag.collection-name=embedded-rag",
                        "rag.chunk.strategy=recursive",
                        "rag.chunk.recursive.max-tokens=321")
                .run(context -> {
                    assertThat(context).hasSingleBean(RagDocumentProperties.class);
                    assertThat(context).hasSingleBean(RagSplitterProperties.class);
                    assertThat(context.getBean(RagDocumentProperties.class).getCollectionName())
                            .isEqualTo("embedded-rag");
                    assertThat(context.getBean(RagSplitterProperties.class).getStrategy())
                            .isEqualTo(RagSplitStrategy.RECURSIVE);
                    assertThat(context.getBean(RagSplitterProperties.class)
                            .getRecursive().getMaxTokens()).isEqualTo(321);
                });
    }
}
