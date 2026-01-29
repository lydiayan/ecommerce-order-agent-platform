package com.example.mallorder.controller;

import com.example.mallorder.entity.Order;
import com.example.mallorder.service.OrderService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/orders")
@Log4j2
public class OrderController {
    @Autowired
    private OrderService orderService;

    // 订单列表查询
    @GetMapping("/list")
    public List<Order> getOrders() {
        log.info("=====》查询所有订单");
        return orderService.getOrders();
    }

    // 订单详情查询（根据订单号）
    @GetMapping("/order/{orderId}")
    public Order getOrderById(@PathVariable(value = "userId") String orderId) {
        log.info("=====》根据订单号查询订单");
        return orderService.getOrderById(orderId);
    }

    // 订单列表查询（根据用户ID）
    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUserId(@PathVariable(value = "userId") String userId) {
        log.info("=====》查询{userId}订单",userId);
        return orderService.getOrdersByUserId(userId);
    }

    // 取消订单（根据订单号）
    @PostMapping("/cancel/{orderId}")
    public boolean cancelOrder(@PathVariable(value = "orderId") String orderId) {
        log.info("=====》取消用户{orderId}订单");
        return orderService.cancelOrder(orderId);
    }
    @PostMapping("/create")
    public Order createOrder(@RequestParam String userId, @RequestParam int productId) {
        log.info("=====》创建订单");
        return orderService.createOrder(userId, productId);
    }


}