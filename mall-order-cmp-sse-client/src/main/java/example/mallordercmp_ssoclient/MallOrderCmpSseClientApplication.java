package example.mallordercmp_ssoclient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MallOrderCmpSseClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderCmpSseClientApplication.class, args);
    }

    //@Bean
    public CommandLineRunner predefinedQuestions(ChatClient.Builder chatClientBuilder, OrderService orderService) {
        String userInput1 = "查询所有订单";
        return args -> {
            var chatClient = chatClientBuilder
                    .defaultSystem("你是一个电商订单查询助手，能够帮助用户查询订单信息。")
                    .build();

            System.out.println("\n>>> QUESTION: " + userInput1);
            
            try {
                String response = chatClient.prompt()
                        .user(promptSpec -> promptSpec
                                .text(userInput1)
                                .param("user_id", "USER1008"))
                        .call()
                        .content();
                
                System.out.println("\n>>> ASSISTANT: " + response);
            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}

@Component
class OrderService {
    
    /**
     * 查询用户订单的工具方法
     */
    public List<Map<String, Object>> getUserOrders(String userId) {
        // 模拟查询用户订单的业务逻辑
        System.out.println("正在查询用户 " + userId + " 的订单...");
        
        // 这里应该是实际的数据库查询逻辑
        // 返回模拟数据作为示例
        return List.of(
            Map.of(
                "orderId", "ORD001",
                "orderDate", "2024-02-05",
                "status", "已发货",
                "amount", 299.90,
                "items", List.of(
                    Map.of("name", "商品A", "quantity", 1, "price", 199.90),
                    Map.of("name", "商品B", "quantity", 1, "price", 100.00)
                )
            ),
            Map.of(
                "orderId", "ORD002",
                "orderDate", "2024-02-01",
                "status", "已完成",
                "amount", 159.50,
                "items", List.of(
                    Map.of("name", "商品C", "quantity", 2, "price", 79.75)
                )
            )
        );
    }
}