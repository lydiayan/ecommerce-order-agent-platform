package com.example.kjlgraphdemo.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.RunnableErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;

public class HumanNode implements NodeAction {
    private static final Logger logger = LoggerFactory.getLogger(HumanNode.class);



    private ChatClient chatClient;

    public HumanNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("humannode节点----------------------》"+state.data().toString());
        HashMap<String, Object> resultMap = new HashMap<>();
        return resultMap;
    }
}
