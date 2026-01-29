package com.example.mallordergraphserver01.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FoodsRepository implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        List<FoodItem> foodList = new ArrayList<>();
        foodList.add(new FoodItem("苹果", "uid1", "https://127.0.0.1:8080"));
        foodList.add(new FoodItem("香蕉", "uid2", "https://127.0.0.1:8080"));
        foodList.add(new FoodItem("普通", "uid3", "https://127.0.0.1:8080"));

        return Map.of("queryFoodsRepository",foodList);
    }

    // 定义FoodItem内部类来表示食材库中的每一项
    public static class FoodItem {
        private String foodName;
        private String fooUid; // 注意：原数据中字段名为"fooUid"，这里保持一致
        private String picUrl;

        public FoodItem(String foodName, String fooUid, String picUrl) {
            this.foodName = foodName;
            this.fooUid = fooUid;
            this.picUrl = picUrl;
        }

        // Getters and Setters
        public String getFoodName() {
            return foodName;
        }

        public void setFoodName(String foodName) {
            this.foodName = foodName;
        }

        public String getFooUid() {
            return fooUid;
        }

        public void setFooUid(String fooUid) {
            this.fooUid = fooUid;
        }

        public String getPicUrl() {
            return picUrl;
        }

        public void setPicUrl(String picUrl) {
            this.picUrl = picUrl;
        }
    }
}