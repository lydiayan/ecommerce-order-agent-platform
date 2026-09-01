package com.css.mallorderagent;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.config.FeedbackProperties;
import com.css.mallorderagent.security.AuthProperties;
import com.example.mallordermilvusrag.MallOrderMilvusRagApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@ComponentScan(
        basePackages = {"com.css.mallorderagent", "com.example.mallordermilvusrag"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MallOrderMilvusRagApplication.class
        )
)
@EnableConfigurationProperties({OrderAgentProperties.class, AuthProperties.class, FeedbackProperties.class})
@EnableScheduling
public class MallOrderAgentApplication {

    @Bean
    WebClient.Builder mcpWebClientBuilder(AuthProperties properties) {
        return WebClient.builder().defaultHeader(
                HttpHeaders.AUTHORIZATION, "Bearer " + properties.getMcpServiceToken());
    }

    public static void main(String[] args) {
        SpringApplication.run(MallOrderAgentApplication.class, args);
    }
}
