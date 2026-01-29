package com.example.kjlgraphdemo.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.kjlgraphdemo.config.CacheManager;
import com.example.kjlgraphdemo.entity.FoodIteam;
import com.example.kjlgraphdemo.mapper.FoodMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoodsRepositoryNode implements NodeAction {
    private static final Logger logger = LoggerFactory.getLogger(FoodsRepositoryNode.class);

    private ChatClient chatClient;

    private FoodMapper foodMapper;

    public FoodsRepositoryNode(ChatClient.Builder chatClientBuilder, FoodMapper foodMapper) {
        this.chatClient = chatClientBuilder.build();
        this.foodMapper = foodMapper;
    }
    /*
    * 获取食材库中食材*/
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("------------"+"获取食材库食材节点"+state.data());
        Map<String, Object> stateMap=state.data();
        List<String> foodIteams=foodMapper.findByFoodnameLike("%"+stateMap.get("query")+"%");
        if (foodIteams.size()==0){
            return Map.of("result","食材库中没有"+stateMap.get("query")+",是否将其加到食材库");
        }else {
            //CacheManager.getCache().put("foods_list", foodIteams);
           return Map.of("result", "食材库中存在"+foodIteams.toString()+",请选择：");
        }
    }
}