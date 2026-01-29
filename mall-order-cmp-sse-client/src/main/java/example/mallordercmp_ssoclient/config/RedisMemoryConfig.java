package example.mallordercmp_ssoclient.config;


import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisMemoryConfig {

    @Value("${spring.ai.memory.redis.host}")
    private String redisHost;

    @Value("${spring.ai.memory.redis.port}")
    private int redisPort;

    @Value("${spring.ai.memory.redis.password}")
    private String redisPassword;

    @Value("${spring.ai.memory.redis.timeout}")
    private int redisTimeout;

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository() {
        return RedisChatMemoryRepository.builder()
                .host(redisHost)
                .port(redisPort)
                .timeout(redisTimeout)
                .build();
    }
}
