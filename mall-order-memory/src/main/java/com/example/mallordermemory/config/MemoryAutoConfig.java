package com.example.mallordermemory.config;

import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallordermemory.memory.LongTermMemoryManager;
import com.example.mallordermemory.memory.RedisShortTermMemoryStore;
import com.example.mallordermemory.memory.ShortTermMemoryManager;
import com.example.mallordermemory.service.MemoryConsolidationService;
import com.example.mallordermemory.service.MemoryExtractor;
import com.example.mallordermemory.service.UserProfileMergeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.Optional;

/**
 * 记忆模块自动配置。
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration"
})
@EnableConfigurationProperties(MemoryProperties.class)
@ConditionalOnProperty(prefix = "memory", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class MemoryAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(MemoryAutoConfig.class);

    private final MemoryProperties properties;

    public MemoryAutoConfig(MemoryProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "memoryRedisConnectionFactory")
    @Primary
    @ConditionalOnMissingBean(name = "memoryRedisConnectionFactory")
    public RedisConnectionFactory memoryRedisConnectionFactory() {
        MemoryProperties.ShortTermProperties.RedisProperties redis = properties.getShortTerm().getRedis();
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(redis.getHost(), redis.getPort());
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(redis.getConnectionTimeoutMs()))
                .build();
        log.info("Creating memory Redis connection factory at {}:{}", redis.getHost(), redis.getPort());
        return new LettuceConnectionFactory(standalone, clientConfig);
    }

    @Bean(name = "memoryRedisTemplate")
    @ConditionalOnMissingBean(name = "memoryRedisTemplate")
    public StringRedisTemplate memoryRedisTemplate(
            @Qualifier("memoryRedisConnectionFactory") RedisConnectionFactory memoryRedisConnectionFactory) {
        return new StringRedisTemplate(memoryRedisConnectionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisShortTermMemoryStore redisShortTermMemoryStore(
                                                               @Qualifier("memoryRedisTemplate") StringRedisTemplate memoryRedisTemplate,
                                                               ObjectMapper objectMapper) {
        MemoryProperties.ShortTermProperties shortTerm = properties.getShortTerm();
        log.info("Creating RedisShortTermMemoryStore, keyPrefix={}, ttlSeconds={}, maxSize={}",
                shortTerm.getKeyPrefix(), shortTerm.getTtlSeconds(), shortTerm.getMaxSize());
        return new RedisShortTermMemoryStore(
                memoryRedisTemplate,
                objectMapper,
                shortTerm.getKeyPrefix(),
                shortTerm.getCursorKeyPrefix(),
                shortTerm.getMaxSize(),
                shortTerm.getTtlSeconds()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ShortTermMemoryManager shortTermMemoryManager(RedisShortTermMemoryStore redisShortTermMemoryStore) {
        return new ShortTermMemoryManager(
                redisShortTermMemoryStore,
                properties.getUserId(),
                properties.getConversationId()
        );
    }

    @Bean
    @ConditionalOnMissingBean(MilvusServiceClient.class)
    public MilvusServiceClient memoryMilvusServiceClient() {
        MemoryProperties.LongTermProperties.MilvusProperties milvus = properties.getLongTerm().getMilvus();
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(milvus.getHost())
                .withPort(milvus.getPort())
                .build();
        log.info("Creating memory Milvus client at {}:{}", milvus.getHost(), milvus.getPort());
        return new MilvusServiceClient(connectParam);
    }

    @Bean
    @ConditionalOnMissingBean
    public LongTermMemoryManager longTermMemoryManager(MilvusServiceClient milvusServiceClient) {
        return new LongTermMemoryManager(milvusServiceClient, properties.getLongTerm().getDimension());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "memory.extractor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MemoryExtractor memoryExtractor(OpenAiChatModel chatModel) {
        log.info("Creating MemoryExtractor");
        return new MemoryExtractor(chatModel);
    }

    @Bean
    @ConditionalOnMissingBean
    public HybridMemoryManager hybridMemoryManager(ShortTermMemoryManager shortTermMemoryManager,
                                                   LongTermMemoryManager longTermMemoryManager) {
        return new HybridMemoryManager(
                shortTermMemoryManager,
                longTermMemoryManager,
                properties.getUserId()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "memory.consolidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MemoryConsolidationService memoryConsolidationService(HybridMemoryManager hybridMemoryManager,
                                                                 MemoryExtractor memoryExtractor,
                                                                 EmbeddingModel embeddingModel,
                                                                 MemoryProperties properties,
                                                                 Optional<UserProfileMergeService> userProfileMergeService) {
        log.info("Creating MemoryConsolidationService, intervalMs={}, messageThreshold={}, tokenThreshold={}",
                properties.getConsolidation().getIntervalMs(),
                properties.getConsolidation().getMessageThreshold(),
                properties.getConsolidation().getTokenThreshold());
        return new MemoryConsolidationService(
                hybridMemoryManager,
                memoryExtractor,
                embeddingModel,
                properties,
                userProfileMergeService
        );
    }

    @Bean
    public MemoryProperties.ConsolidationProperties memoryConsolidationProperties() {
        return properties.getConsolidation();
    }
}
