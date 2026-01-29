package com.example.kjlgraphdemo.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

import java.util.Optional;


@Slf4j
public class HumanFeedbackDispatcher implements EdgeAction {

    private final ChatClient chatClient;


    public HumanFeedbackDispatcher(ChatClient.Builder builder) {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder().build();
        this.chatClient = builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build()).build();
    }

    @Override
    public String apply(OverAllState state) throws Exception {
        //是否需要添加信息
        // 先取人工输入,可前端传入
        OverAllState.HumanFeedback feedback =state.humanFeedback();
        String feedbackNode = feedback.nextNodeId();
        // 如果人工没给，就让模型来判断
        if (""==feedback.nextNodeId()) {
            String context = Optional.ofNullable(feedback.data().get("feed_back"))
                    .map(Object::toString)
                    .orElse("无上下文");
            String prompt1="把一种食物换成另一种食物，例如：把白苹果换成炒粉，则需要识别为ChangeFoodNode";
            String prompt2="我想吃{食物}，例如我想吃苹果，需要识别为FoodsRepositoryNode";
            String prompt3="选择{食物}，例如选择烤鸡翅，需要识别为GenerateMealNode";
            String prompt4="有关键字：添加或者某天(今天，明天)某一餐（早餐，中餐，晚餐），例如选择烤鸡翅，需要识别为AddInformationNode";
            String prompt = """
                你是一个node选择助手，根据content信息选择节点"""
                    +prompt1+prompt2+prompt3+prompt4+
                """
                 -请只返回下面四种选项之一，表示下一步节点：
                 - ChangeFoodNode
                 - GenerateMealNode
                 - AddInformationNode
                 - query
                请严格只返回这几个字符串之一，不要添加多余文字。，
                content如下：
                """ + context;
            feedbackNode = chatClient.prompt(prompt).call().content().toString();
        }
        log.info("HumanFeedbackDispatcher返回下一节点为："+feedbackNode);
        //return feedbackNode;\
        return feedbackNode;
    }
}
