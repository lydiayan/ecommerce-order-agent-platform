package com.example.mallorder.controller;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/post")
public class PostController {

    /**
     * 示例POST请求接口
     * 接收JSON数据并返回处理结果
     */
    @PostMapping("/data")
    public Map<String, Object> postData(@RequestBody Map<String, Object> requestData) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 处理接收到的数据
            System.out.println("Received POST data: " + requestData);
            
            // 模拟数据处理逻辑
            response.put("status", "success");
            response.put("message", "数据接收成功");
            response.put("receivedData", requestData);
            response.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "处理数据时发生错误: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * 创建订单的POST接口
     */
    @PostMapping("/order")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> orderData) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 模拟创建订单的逻辑
            System.out.println("Creating order with data: " + orderData);
            
            response.put("status", "success");
            response.put("message", "订单创建成功");
            response.put("orderId", "ORD" + System.currentTimeMillis()); // 生成订单ID
            response.put("orderData", orderData);
            response.put("createTime", System.currentTimeMillis());
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "创建订单时发生错误: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * 用户登录的POST接口
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> credentials) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = credentials.get("username");
            String password = credentials.get("password");
            
            System.out.println("Login attempt for user: " + username);
            
            // 模拟验证逻辑
            if (username != null && password != null && !username.isEmpty() && !password.isEmpty()) {
                response.put("status", "success");
                response.put("message", "登录成功");
                response.put("token", "Bearer " + System.currentTimeMillis()); // 模拟生成token
                response.put("user", username);
            } else {
                response.put("status", "error");
                response.put("message", "用户名或密码不能为空");
            }
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "登录时发生错误: " + e.getMessage());
        }
        
        return response;
    }
}