package com.example.mallordercmpserver.service;
import com.example.mallordercmpserver.data.Order;
import com.example.mallordercmpserver.data.Product;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: jyy
 * @Desc:
 **/
@Service
public class OpenOrderService {

    private static final String BASE_URL = "http://localhost:8081";

    private final RestTemplate restTemplate;

    private final WebClient webClient;
    public OpenOrderService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8081")
                .build();
        this.restTemplate = new RestTemplate();
    }

    @Tool(description = "获取所有订单信息" )
    public List<Order> getOrders() {
        // 尝试远程调用
        System.out.println("获取所有订单信息");
        String url = BASE_URL+"/orders/list";
        return restTemplate.getForObject(url, List.class);
    }

    @Tool(description = "根据用户ID获取用户订单列表信息" )
    public List<Order> getOrdersByUserId(String userId) {
        System.out.println("根据用户ID获取用户订单列表信息");
        // 尝试远程调用
        List<Order> response=new ArrayList<>();
        try {
            response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders/user/"+userId)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
            System.out.println(userId+"的订单数量：" + response.size());
            // 解析响应并返回格式化的天气信息
            for (Order order : response){
                System.out.println("订单ID：" + order.getOrderId());
            }

        } catch (Exception e) {
            //return "获取信息失败：" + e.getMessage();
        }
        return  response;
    }
    @Tool(description = "根据订单ID获取订单详情" )
    public Order getOrderById(String orderId) {
        System.out.println("根据订单ID获取订单详情");
        String url = BASE_URL+"/orders/order"+"/{orderId}";
        return restTemplate.getForObject(url, Order.class, orderId);
    }

    @Tool(description = "根据订单ID取消订单" )
    public boolean cancelOrder(String orderId) {
        System.out.println("根据订单ID取消订单");
        String url = BASE_URL+"/orders/cancel/{orderId}";
        return restTemplate.postForObject(url, null, Boolean.class, orderId);
    }


    @Tool(description = "根据分类ID获取商品列表")
    public List<Product> getProductsByCategory(int categoryId) {
        System.out.println("根据分类ID获取商品列表");
        String url = BASE_URL + "/products/category/" + categoryId;
        return restTemplate.getForObject(url, List.class);
    }

    @Tool(description = "根据商品名称搜索商品")
    public List<Product> getProductsByName(String productName) {
        System.out.println("根据商品名称搜索商品");
        String url = BASE_URL + "/products/search?productName=" + productName;
        return restTemplate.getForObject(url, List.class);
    }

    @Tool(description = "添加新商品")
    public int addProduct(Product product) {
        System.out.println("添加新商品");
        String url = BASE_URL+"/products";
        return restTemplate.postForObject(url, product, Integer.class);
    }

    @Tool(description = "更新商品信息")
    public int updateProduct(Product product) {
        System.out.println("更新商品信息");
        String url = BASE_URL+"/products";
        return restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(product), Integer.class).getBody();
    }

    @Tool(description = "根据商品ID删除商品")
    public int deleteProduct(int productId) {
        System.out.println("根据商品ID删除商品");
        String url = BASE_URL + "/products/" + productId;
        restTemplate.delete(url);
        // DELETE请求通常不返回内容，这里返回1表示成功执行
        return 1;
    }
}
