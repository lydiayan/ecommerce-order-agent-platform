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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AddInformationNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(AddInformationNode.class);

    private final ChatClient chatClient;
    private final FoodMapper foodMapper;

    public AddInformationNode(ChatClient.Builder builder, FoodMapper foodMapper) {
        this.chatClient = builder.build();
        this.foodMapper = foodMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("AddInformationNode-----------添加食物节点---------");
        String feedback = state.humanFeedback().data().get("feed_back").toString();
        // 更明确的提示，强制要求返回JSON格式
        String prompt = """
            你是一个信息提取助手。请从用户反馈中提取以下信息并以严格的JSON格式返回。
            要求：
            1. 必须只返回JSON，不要添加任何其他文字或解释
            2. JSON格式必须严格如下：
            {"mealdate":"日期信息","mealKind":"餐别信息","addbz":false}
            
            提取规则：
            - mealdate：提取日期相关词汇（如：今天、明天、后天等）
            - mealKind：提取餐别（只能是：早餐、中餐、晚餐）
            - addbz：添加/可以等等一切表示同意的词,返回boolean类型，若没有默认为false
            示例：
            用户说："明天早餐吃包子"
            返回：{"mealdate":"明天","mealKind":"早餐","addbz":true}
            
            现在请处理用户反馈：
            """ + feedback;
        String datas = chatClient.prompt(prompt).call().content().toString();
        // 将JSON字符串解析为JSONObject

        JSONObject jsonObject = JSON.parseObject(datas);
        // 将JSONObject转换为Map
        Map<String, Object> feedbackMap = (Map<String, Object>) jsonObject;
        boolean addbz=(boolean)feedbackMap.get("addbz");
        List<String> foods_listnew=null;
        if (addbz){
            //替换食材库查询结果
            List<String> foods_list= (List<String>) CacheManager.getCache().getIfPresent("foods_list");
            Map<String,Object> changefood= (Map<String, Object>)state.data().get("changefood");
            String source= (String) changefood.get("source");
            String target=(String) changefood.get("target");;
            foods_listnew=foods_list.parallelStream().map(s->s.equals(source)?target:s).collect(Collectors.toList());
            CacheManager.getCache().put("foods_list", foods_listnew);
        }
        String aiReturn= "";
        //添加信息
        Map<String,Object> infomation= (Map<String, Object>) CacheManager.getCache().getIfPresent("mealplanInfomation");
        String m1=(String)infomation.get("mealKind");//缓存中是否有mealKind信息
        String nextNode="GenerateMealNode";
        String mealkind= (String) feedbackMap.get("mealKind");
        if (!isNullOrEmpty(mealkind)){//取最新反馈数据
            infomation.put("mealKind",mealkind);
        }else if (isNullOrEmpty(m1)){
            aiReturn="请补充是早餐，晚饭，午饭？";
        }
        String mealdate= (String) feedbackMap.get("mealdate");
        String m2=(String)infomation.get("mealplanInfomation");
        if (!isNullOrEmpty(mealdate)){
            infomation.put("mealData",mealdate);
            CacheManager.getCache().put("mealplanInfomation", infomation);
        }else if (isNullOrEmpty(m2)){
            aiReturn="请补充哪天吃？";
        }
        Map<String,Object> resultMap=new HashMap<>();
        if (!isNullOrEmpty(aiReturn)) {
            resultMap.put("aiReturn", aiReturn);
            nextNode = "human_feedback";
            return Map.of("result", resultMap, "nextNode", nextNode);
        } else {
            // 直接生成菜单
            nextNode = "GenerateMealNode";
            return Map.of("result", resultMap,  "nextNode", nextNode);
        }

    }
    public boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }


}
