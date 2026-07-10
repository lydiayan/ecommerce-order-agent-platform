package com.example.mallorderobservability.config;

import com.example.mallorderobservability.trace.LoggingTracePublisher;
import com.example.mallorderobservability.trace.RagTraceService;
import com.example.mallorderobservability.trace.RocketMqTracePublisher;
import com.example.mallorderobservability.trace.TracePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityProducerAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public TracePublisher tracePublisher(ObservabilityProperties properties,
                                         ObjectMapper objectMapper,
                                         ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (properties.getProducer().isEnabled() && rocketMQTemplate != null) {
            return new RocketMqTracePublisher(rocketMQTemplate, properties, objectMapper);
        }
        return new LoggingTracePublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    public RagTraceService ragTraceService(TracePublisher tracePublisher,
                                           ObservabilityProperties properties) {
        return new RagTraceService(tracePublisher, properties);
    }
}
