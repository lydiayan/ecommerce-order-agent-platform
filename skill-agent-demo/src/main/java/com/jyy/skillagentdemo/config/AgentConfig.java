package com.jyy.skillagentdemo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Configuration
 * 
 * Spring AI Alibaba Studio 会自动加载 skills 并使其可用
 * 智能体会根据任务描述自动发现并使用合适的 skill
 */
@Configuration
public class AgentConfig {
    
    /**
     * 配置 ChatClient Builder
     * Spring AI Alibaba Studio 会自动将 skills 集成到 ChatClient 中
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个智能助手，拥有多种技能（skills）可以帮助用户完成任务。"
                        + "当你收到用户的任务时，请分析任务需求，自动选择合适的 skill 来完成任务。"
                        + "可用的 skills 包括："
                        + "1. web-research: 用于进行网络研究和信息收集"
                        + "2. arxiv-search: 用于搜索 arXiv 学术论文"
                        + "3. skill-creator: 用于创建新的技能"
                        + "请根据任务内容自动判断需要使用哪个 skill，无需用户明确指定。");
    }
}
