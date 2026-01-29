package com.example.kjlgraphdemo.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.kjlgraphdemo.config.CacheManager;
import com.example.kjlgraphdemo.entity.MealData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GenerateMealNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(GenerateMealNode.class);

    private ChatClient.Builder chatClientBuilder;
    public GenerateMealNode(ChatClient.Builder chatClientBuilder) {
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("生成食谱节点======>",state.data());
        Map<String,Object> infomation= (Map<String, Object>) CacheManager.getCache().getIfPresent("mealplanInfomation");
        String mealkind= (String)infomation.get("mealKind");
        String mealdate= (String)infomation.get("mealData");
        List<String> foods_list= (List<String>) CacheManager.getCache().getIfPresent("foods_list");
        MealData mealplan=new MealData();
        mealplan.setMealkind(mealkind);
        mealplan.setMealdate(mealdate);
        mealplan.setMeasl(foods_list);
        return Map.of("result",mealplan);
    }
}
