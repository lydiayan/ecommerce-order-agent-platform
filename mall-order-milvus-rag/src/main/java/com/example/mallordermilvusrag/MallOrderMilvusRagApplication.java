package com.example.mallordermilvusrag;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RagDocumentProperties.class, RagSplitterProperties.class})
public class MallOrderMilvusRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderMilvusRagApplication.class, args);
    }
}
