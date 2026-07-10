package com.example.mallordermilvusrag.config;

import com.example.mallordermilvusrag.tracing.RagTracingConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

/**
 * 构建 RAG 问答专用 {@link ChatClient}（clone 隔离，避免污染 Builder 单例）。
 * LLM trace 由 {@link com.example.mallordermilvusrag.tracing.RagTracingAdvisor}
 * 在每次 {@code prompt().advisors(...)} 时挂载。
 */
@AutoConfiguration
@AutoConfigureAfter(RagTracingConfiguration.class)
public class RagAskChatClientConfiguration {

    @Bean
    ChatClient ragAskChatClient(ChatClient.Builder chatClientBuilder,
                                RagDocumentProperties ragDocumentProperties) {
        // 使用 clone() 避免污染 Spring 容器中的 Builder 单例
        return chatClientBuilder.clone()
                .defaultSystem(ragDocumentProperties.getAsk().getSystemPrompt())
                .build();
    }
}
