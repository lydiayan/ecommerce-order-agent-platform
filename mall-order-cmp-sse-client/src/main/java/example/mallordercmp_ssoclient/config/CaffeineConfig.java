package example.mallordercmp_ssoclient.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineConfig {

    @Bean
    // 缓存校验失败的列表
    public Cache<String, List<?>> failedImportCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000) // 最多缓存 1000 个导入任务
                .expireAfterWrite(30, TimeUnit.MINUTES) // 缓存 30 分钟自动过期
                .build();
    }
}
