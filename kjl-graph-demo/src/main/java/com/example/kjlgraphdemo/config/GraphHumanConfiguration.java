package com.example.kjlgraphdemo.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.kjlgraphdemo.dispatcher.ChangeFoodNodeDispather;
import com.example.kjlgraphdemo.dispatcher.HumanFeedbackDispatcher;
import com.example.kjlgraphdemo.mapper.FoodMapper;
import com.example.kjlgraphdemo.node.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;


@Configuration
public class GraphHumanConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphHumanConfiguration.class);
    //ChatMemory chatMemory = new InMemoryChatMemory();

    @Autowired
    private FoodMapper foodMapper;
    @Bean
    public StateGraph humanGraph(ChatClient.Builder chatClientBuilder) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            //Map<String, ReplaceStrategy> keyStrategyHashMap = new HashMap<>();
            // 人类反馈替换策略
            keyStrategyHashMap.put("query", new ReplaceStrategy());         // 初始查询条件
            //keyStrategyHashMap.put("foods_list", new ReplaceStrategy());    // 选择的结果
            keyStrategyHashMap.put("feed_back", new ReplaceStrategy());     // 用户反馈
            keyStrategyHashMap.put("result", new ReplaceStrategy());
            keyStrategyHashMap.put("nextNode", new ReplaceStrategy());
            //keyStrategyHashMap.put("mealplanInfomation", new ReplaceStrategy());
            keyStrategyHashMap.put("changefood", new ReplaceStrategy());
            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                // 1. 查询食材库
                .addNode("query", node_async(new FoodsRepositoryNode(chatClientBuilder, foodMapper)))
                // 2. 用户反馈节点
                .addNode("human_feedback", node_async(new HumanNode(chatClientBuilder)))

                // 3. 根据反馈修改食材选择
                .addNode("ChangeFoodNode", node_async(new ChangeFoodNode(chatClientBuilder,foodMapper)))

                // 4. 生成最终结果节点
                .addNode("GenerateMealNode", node_async(new GenerateMealNode(chatClientBuilder)))
                // 5. 添加信息节点
                .addNode("AddInformationNode", node_async(new AddInformationNode(chatClientBuilder,foodMapper)))

                // 流程起点
                .addEdge(StateGraph.START, "query")

                // 查询完成后进入人工选择
                .addEdge("query", "human_feedback")

                // 用户反馈后分支
                .addConditionalEdges(
                        "human_feedback",
                        AsyncEdgeAction.edge_async(new HumanFeedbackDispatcher(chatClientBuilder)),
                        Map.of("query","query","ChangeFoodNode","ChangeFoodNode", "GenerateMealNode","GenerateMealNode", "AddInformationNode","AddInformationNode")
                )
                .addEdge("GenerateMealNode", "human_feedback")
                .addConditionalEdges(
                        "AddInformationNode",
                        AsyncEdgeAction.edge_async(new ChangeFoodNodeDispather()),
                        Map.of("human_feedback","human_feedback","GenerateMealNode","GenerateMealNode")
                )
                .addConditionalEdges(
                        "ChangeFoodNode",
                        AsyncEdgeAction.edge_async(new ChangeFoodNodeDispather()),
                        Map.of("human_feedback","human_feedback","GenerateMealNode","GenerateMealNode")
                );


        // 打印 PlantUML 拓扑图
        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML);
        logger.info("\n=== human graph ===");
        logger.info(representation.content());
        logger.info("===================\n");

        return stateGraph;
    }
}
