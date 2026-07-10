package com.example.mallorderobservability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Trace 消费端：RocketMQ → Elasticsearch，并提供查询 API。
 */
@SpringBootApplication
public class MallOrderObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderObservabilityApplication.class, args);
    }
}
