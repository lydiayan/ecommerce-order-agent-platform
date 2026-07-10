package com.css.mallorderagent;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.example.mallordermilvusrag.MallOrderMilvusRagApplication;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = {"com.css.mallorderagent", "com.example.mallordermilvusrag"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MallOrderMilvusRagApplication.class
        )
)
@EnableConfigurationProperties({RagDocumentProperties.class, OrderAgentProperties.class})
public class MallOrderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderAgentApplication.class, args);
    }
}
