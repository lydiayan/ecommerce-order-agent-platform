package com.example.kjlgraphdemo.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.kjlgraphdemo.config.CacheManager;
import com.example.kjlgraphdemo.entity.FoodIteam;
import com.example.kjlgraphdemo.mapper.FoodMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AiNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(AiNode.class);


    private final ChatClient chatClient;
    private final FoodMapper foodMapper;

    public AiNode(ChatClient.Builder builder, FoodMapper foodMapper) {
        this.chatClient = builder.build();
        this.foodMapper = foodMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("------------"+"进入ainode节点");
        String feedback=(String)state.data().get("feed_back");
        if (null!=state.humanFeedback())
        feedback = state.humanFeedback().data().get("feed_back").toString();
        String prompt = """
                你是一个关键信息提取助手。
                从 content 文本中提取以下信息，并严格按指定 JSON 格式返回（不要输出多余文字，不要解释）：
                
                1. "choose"：
                   - 如果文本包含与“选择”含义相同或相近的词（如“选”、“挑”、“要”等），返回 "Y"；
                   - 如果没有找到，返回 "N"。
                
                2. "foods"：
                   - 在 choose = "Y" 时，提取与食物相关的词（如“苹果”、“米饭”、“牛奶”等），多个词用英文逗号分隔；
                   - 如果没有找到，返回 ""。
                
                3. "add"：
                   - 如果文本中包含“添加”、“可以”、“加”等表示同意的词，返回 "Y"；
                   - 如果包含“不加”、“不要”、“去掉”等表示否定的词，返回 "N"；
                   - 如果都没有出现，返回 ""。
                
                4. "changeFood"：
                   - 如果有更换食材的意图，返回 "Y"，否则返回 "N"。
                   - 同时提取：
                     • "source"：被替换的原食物A；
                     • "target"：替换成的新食物B；
                   - 如果未检测到更换意图，source 和 target 返回空字符串 ""。
                
                5. "query" 与 "queryTarget"：
                   - 想吃苹果，如果识别想，吃，查询等查询意图，"query" 返回 "Y"，否则返回 ""；
                   - "queryTarget"：提取需要查询的具体对象或内容（如“苹果”），如我想吃苹果，查询食材库，则提取苹果。
                
                6. "mealKind" 与 "mealDate"：
                   - "mealKind"：提取餐类（如“早餐”、“午餐”、“晚餐”），没有则返回 ""；
                   - "mealDate"：提取日期或时间（如“今天”、“明天”），没有则返回 ""。
                
                **必须严格返回以下 JSON 格式：**
                {
                  "choose": "<Y/N>",
                  "foods": "<食物相关词，英文逗号分隔或空字符串>",
                  "add": "<Y/N或空字符串>",
                  "changeFood": "<Y/N>",
                  "source": "<原食物或空字符串>",
                  "target": "<新食物或空字符串>",
                  "query": "<Y/N或空字符串>",
                  "queryTarget": "<查询对象或空字符串>",
                  "mealKind": "<餐类或空字符串>",
                  "mealDate": "<日期或空字符串>"
                }
                
                content: "{{content}}"
                """ + feedback;
        String jsonString = chatClient.prompt(prompt).call().content().toString();
        // 将JSON字符串解析为JSONObject
        JSONObject jsonObject = JSON.parseObject(jsonString);
        // 将JSONObject转换为Map
        //1、查询数据库
        Map<String, Object> feedbackmap = (Map<String, Object>) jsonObject;
        if ("Y".equals((String)feedbackmap.get("query"))){
            List<String> foodIteams=foodMapper.findByFoodnameLike("%"+(String)feedbackmap.get("queryTarget")+"%");
            if (foodIteams.size()==0){
                CacheManager.getCache().put("addFood",(String)feedbackmap.get("queryTarget"));
                return Map.of("result","食材库中没有"+(String)feedbackmap.get("queryTarget")+",是否将其加到食材库");
            }else {
                return Map.of("result", "食材库中存在"+foodIteams.toString()+",请选择：");
            }
        }


        //2、更换食材
        if ("Y".equals((String)feedbackmap.get("changeFood"))){
            String source=(String)feedbackmap.get("source");//源
            String target=(String)feedbackmap.get("target");//
            Map<String,Object> replaceFood= (Map<String, Object>) CacheManager.getCache().getIfPresent("replaceFood");
            replaceFood.replace("source",source);
            replaceFood.put("target",target);
            List<String> foodIteams=foodMapper.findByFoodnameLike("%"+target+"%");
            Map<String, Object> result=new HashMap<>();
            String nextNode="";
            if (foodIteams.size()==1) {//存在
                List<String> foods_list = (List<String>) CacheManager.getCache().getIfPresent("foods_list");
                //替换食材库查询结果
                //List<String> foods_list= (List<String>) state.data().get("foods_list");
                List<String> foods_listnew = foods_list.parallelStream().map(s -> s.equals(source) ? target : s).collect(Collectors.toList());
                CacheManager.getCache().put("foods_list", foods_listnew);

            }else if(foodIteams.size()>1){
                result.put("aiReturn","食材库中发现"+foodIteams+"请选择");
                nextNode="human_feedback";
                return Map.of("result", result,"nextNode",nextNode);
            }else{//不存在，需要用户确认是否添加到食材库
                result.put("aiReturn",feedbackmap.get("target")+"不存在，是否添加到食材库");
                CacheManager.getCache().put("addFood",(String)feedbackmap.get("target"));

                return Map.of("result", result);
            }
        }
        if("Y".equals((String)feedbackmap.get("add"))){
            String addFood=(String)CacheManager.getCache().getIfPresent("addFood");
            foodMapper.insertFood(new FoodIteam(addFood,"http://localhost:8080"));
            feedbackmap.replace("choose","Y");
            Map<String, Object> replaceFood = (Map<String, Object>) CacheManager.getCache().getIfPresent("replaceFood");
            replaceFood.replace("target",addFood);
        }
        if ("Y".equals((String)feedbackmap.get("choose"))){
            //查看是否是替换
            Map<String,Object> replaceFood= (Map<String, Object>) CacheManager.getCache().getIfPresent("replaceFood");
            String source=(String)replaceFood.get("source");
            String target=(String)replaceFood.get("target");
            if (!isNullOrEmpty(target)){
                String c_foods=(String)CacheManager.getCache().getIfPresent("food_list");
                String ncfoods=c_foods.replace(source,target);
                CacheManager.getCache().put("food_list",ncfoods);
                logger.info("替换食材后：{}",ncfoods);
            }else {
                CacheManager.getCache().put("food_list",(String)feedbackmap.get("foods"));
            }
        }
        //2、添加信息(早、中、晚) （哪天）
        StringBuilder aiReturn= new StringBuilder();
        Map<String,Object> cache_infomation= (Map<String, Object>) CacheManager.getCache().getIfPresent("mealplanInfomation");
        String cache_mealKind=(String)cache_infomation.get("mealKind");//缓存中是否有mealKind信息
        String fb_mealkind= (String) feedbackmap.get("mealKind");
        if (!isNullOrEmpty(fb_mealkind)){//取最新反馈数据
            cache_infomation.put("mealKind",fb_mealkind);
        }else if (isNullOrEmpty(cache_mealKind)){
            aiReturn.append("请补充是早餐，晚饭，午饭？");
        }
        String fb_mealdate= (String) feedbackmap.get("mealDate");
        String cache_mealdate=(String)cache_infomation.get("mealDate");
        if (!isNullOrEmpty(fb_mealdate)){
            cache_infomation.put("mealData",fb_mealdate);
            CacheManager.getCache().put("mealplanInfomation", cache_infomation);
        }else if (isNullOrEmpty(cache_mealdate)){
            aiReturn.append("请补充哪天吃？");
        }
        //4、检查所有信息是否都包含了，包含了，生成食谱
        String c_foods=(String)CacheManager.getCache().getIfPresent("food_list");
        //Map<String,Object> cache_minfomation=(Map<String, Object>) CacheManager.getCache().getIfPresent("mealplanInfomation");
        String cache_mealdateN=(String)cache_infomation.get("mealData");
        String cache_mealKindN=(String)cache_infomation.get("mealKind");
        if (c_foods!=null&&!isNullOrEmpty(cache_mealdateN)&&!isNullOrEmpty(cache_mealKindN)){
            Map<String, Object> mealplanMap=new HashMap<>();
            mealplanMap.put("mealkind",cache_mealKindN);
            mealplanMap.put("mealdate",cache_mealdateN);
            mealplanMap.put("foods",c_foods);
            return Map.of("result", mealplanMap);
        }
        return Map.of("result", aiReturn.toString());

    };


    public boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
