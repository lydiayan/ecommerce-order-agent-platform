package com.example.mallordermilvusrag.config;

import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 注册 RAG 模块对外提供的配置属性。
 *
 * <p>独立运行和作为依赖嵌入其他应用时，都由 RAG 模块负责提供这些属性 Bean。</p>
 */
@AutoConfiguration(before = RagAskChatClientConfiguration.class)
@EnableConfigurationProperties({RagDocumentProperties.class, RagSplitterProperties.class})
public class RagPropertiesAutoConfiguration {
}
