package com.example.kjlgraphdemo.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.kjlgraphdemo.config.CacheManager;
import com.example.kjlgraphdemo.entity.FoodIteam;
import com.example.kjlgraphdemo.entity.MealData;
import com.example.kjlgraphdemo.mapper.FoodMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChangeFoodNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ChangeFoodNode.class);


    private final ChatClient chatClient;
    private final FoodMapper foodMapper;

    public ChangeFoodNode(ChatClient.Builder builder, FoodMapper foodMapper) {
        this.chatClient = builder.build();
        this.foodMapper = foodMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("------------"+"进入修改食物节点");
        String feedback = state.humanFeedback().data().get("feed_back").toString();
        String prompt= """
                {
                            "instruction": "提取文本中的食物，并将其替换为指定的食物。请严格按照以下 JSON 格式返回结果：{'source': '原食物', 'target': '新食物'}。且只返回json",
                                "example": "例如，将 '黄苹果' 替换为 '炒粉'，返回的 JSON 应为：{'source': '黄苹果', 'target': '炒粉'}。",
                                "feedback": "请处理以下文本："
                        }
                """+feedback;
        String foods = chatClient.prompt(prompt).call().content().toString();
        // 将JSON字符串解析为JSONObject
        JSONObject jsonObject = JSON.parseObject(foods);
        // 将JSONObject转换为Map
        Map<String, Object> map = (Map<String, Object>) jsonObject;
        String source=(String)map.get("source");//源
        String target=(String)map.get("target");//
        List<String> foodIteams=foodMapper.findByFoodnameLike("%"+target+"%");
        Map<String, Object> result=new HashMap<>();
        String nextNode="";
        if (foodIteams.size()==1){//存在
            List<String> foods_list= (List<String>) CacheManager.getCache().getIfPresent("foods_list");
            //替换食材库查询结果
           //List<String> foods_list= (List<String>) state.data().get("foods_list");
           List<String> foods_listnew=foods_list.parallelStream().map(s->s.equals(source)?target:s).collect(Collectors.toList());
           CacheManager.getCache().put("foods_list",foods_listnew);
           Map<String,Object> infomation= (Map<String, Object>) CacheManager.getCache().getIfPresent("mealplanInfomation");
            String mealkind= (String)infomation.get("mealKind");
           String mealdate= (String)infomation.get("mealData");
           StringBuilder aiReturn= new StringBuilder();
           if (isNullOrEmpty(mealdate)){
               aiReturn.append("请补充是早餐，晚饭，午饭？");
           }
            if (isNullOrEmpty(mealkind)){
                aiReturn.append("请补充哪天吃？");
            }
            result.put("aiReturn",aiReturn);
            if (!isNullOrEmpty(aiReturn.toString())){
                nextNode="human_feedback";
            }else {
                nextNode="GenerateMealNode";
            }
            return Map.of("result", result,"nextNode",nextNode);
        }else if(foodIteams.size()>1){
            result.put("aiReturn","食材库中发现"+foodIteams+"请选择");
            nextNode="human_feedback";
            return Map.of("result", result,"nextNode",nextNode);
        }else{//不存在，需要用户确认是否添加到食材库
            result.put("aiReturn",map.get("target")+"不存在，是否添加到食材库");
            Map<String,Object> replaceFood= (Map<String, Object>) CacheManager.getCache().getIfPresent("replaceFood");
            replaceFood.replace("source",map.get("source").toString());
            replaceFood.put("target",map.get("target").toString());
            nextNode="human_feedback";
            return Map.of("result", result,"nextNode",nextNode);
        }

    }

    public boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
