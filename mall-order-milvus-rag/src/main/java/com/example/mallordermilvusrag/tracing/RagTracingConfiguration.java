package com.example.mallordermilvusrag.tracing;

import com.example.mallorderobservability.config.ObservabilityProducerAutoConfig;
import com.example.mallorderobservability.trace.RagTraceService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration that registers RAG tracing hooks into Spring AI components.
 *
 * <p>Activated when the observability module's {@link RagTraceService} bean
 * is present in the context.
 */
@AutoConfiguration
@AutoConfigureAfter(ObservabilityProducerAutoConfig.class)
@ConditionalOnBean(RagTraceService.class)
public class RagTracingConfiguration {

    @Bean
    RagTracingAdvisor ragTracingAdvisor(RagTraceService ragTraceService) {
        return new RagTracingAdvisor(ragTraceService);
    }

    @Bean
    @Primary
    EmbeddingModel ragTracingEmbeddingModel(EmbeddingModel delegate, RagTraceService ragTraceService) {
        return new RagTracingEmbeddingModel(delegate, ragTraceService);
    }
}
