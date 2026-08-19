package com.example.mallorderobservability.config;

import com.example.mallorderobservability.trace.LoggingTracePublisher;
import com.example.mallorderobservability.trace.RagTraceService;
import com.example.mallorderobservability.trace.RocketMqTracePublisher;
import com.example.mallorderobservability.trace.TracePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(RocketMQAutoConfiguration.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityProducerAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityProducerAutoConfig.class);

    @Bean
    @ConditionalOnMissingBean(TracePublisher.class)
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnProperty(prefix = "observability.producer", name = "enabled", havingValue = "true")
    public TracePublisher rocketMqTracePublisher(RocketMQTemplate rocketMQTemplate,
                                                 ObservabilityProperties properties,
                                                 ObjectMapper objectMapper) {
        log.info("TracePublisher using RocketMQ -> topic={}:{}",
                properties.getTrace().getTopic(), properties.getTrace().getTag());
        return new RocketMqTracePublisher(rocketMQTemplate, properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(TracePublisher.class)
    public TracePublisher loggingTracePublisher() {
        log.warn("TracePublisher falling back to LoggingTracePublisher "
                + "(set observability.producer.enabled=true and ensure RocketMQTemplate is available)");
        return new LoggingTracePublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    public RagTraceService ragTraceService(TracePublisher tracePublisher,
                                           ObservabilityProperties properties) {
        return new RagTraceService(tracePublisher, properties);
    }
}
