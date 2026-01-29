package com.example.kjlgraphdemo.tools;

import com.example.kjlgraphdemo.entity.FoodIteam;
import com.example.kjlgraphdemo.mapper.FoodMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MealServiceTool {

    @Autowired
    private FoodMapper foodMapper;

    /**
     * 根据食物名称模糊查询食物列表
     * @param foodName 食物名称关键词
     * @return 匹配的食物名称列表
     */
    @Tool(description = "根据食物名称模糊查询食物列表")
    public List<String> findFoodsByName(@ToolParam(description = "获取文本中的食物名称")String foodName) {
        return foodMapper.findByFoodnameLike("%" + foodName + "%");
    }

    /**
     * 检查食物是否存在
     * @param foodName 食物名称
     * @return 如果食物存在返回true，否则返回false
     */
    @Tool(description = "根据食物名称判断食物是否存在")
    public boolean isFoodExists(@ToolParam(description = "获取文本中的食物名称")String foodName) {
        List<String> foods = foodMapper.findByFoodnameLike("%" + foodName + "%");
        return !foods.isEmpty();
    }

    //
    @Tool(description = "添加食物到食材库")
    public void inserFoods(@ToolParam(description = "根据提取到的食物，填充FoodIteam")FoodIteam foodIteam) {
        foodMapper.insertFood(foodIteam);
    }
}
