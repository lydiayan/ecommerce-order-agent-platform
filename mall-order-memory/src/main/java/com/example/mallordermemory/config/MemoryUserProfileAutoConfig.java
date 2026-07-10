package com.example.mallordermemory.config;

import com.example.mallordermemory.profile.UserProfileRepository;
import com.example.mallordermemory.service.UserProfileMergeService;
import com.example.mallordermemory.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 用户画像 MySQL 自动配置。
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "memory.user-profile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MemoryUserProfileAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(MemoryUserProfileAutoConfig.class);

    @Bean
    @ConditionalOnMissingBean
    public UserProfileRepository userProfileRepository(JdbcTemplate jdbcTemplate) {
        log.info("Creating UserProfileRepository (MySQL user_profile)");
        return new UserProfileRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserProfileService userProfileService(UserProfileRepository userProfileRepository) {
        return new UserProfileService(userProfileRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(OpenAiChatModel.class)
    public UserProfileMergeService userProfileMergeService(UserProfileRepository userProfileRepository,
                                                           OpenAiChatModel chatModel,
                                                           ObjectMapper objectMapper) {
        log.info("Creating UserProfileMergeService");
        return new UserProfileMergeService(userProfileRepository, chatModel, objectMapper);
    }
}
