package com.jyy.skillagentdemo.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public class skillscontroller {
    private static final Logger log = LoggerFactory.getLogger(skillscontroller.class);
    private final ChatClient chatClient;
    public skillscontroller(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem("你是一个电商助手，能够帮助用户查询商品信息，订单信息，用户信息，以及进行问答。").build();
    }

    @GetMapping("call")
    public String call(@RequestParam(value = "query",defaultValue = "你好，我是商城助手") String query, @RequestParam(value = "conversationId",defaultValue = "zhushou") String conversationId) {
        return chatClient.prompt(query).advisors(a->a.param("CONVERSATIONID", conversationId)).call().content();
    }
}
