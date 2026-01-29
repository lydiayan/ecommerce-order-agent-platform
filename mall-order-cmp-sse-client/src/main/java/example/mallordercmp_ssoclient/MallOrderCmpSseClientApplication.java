package example.mallordercmp_ssoclient;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;


@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MallOrderCmpSseClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderCmpSseClientApplication.class, args);
    }


    @Bean
    public CommandLineRunner predefinedQuestions(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools,
                                                 ConfigurableApplicationContext context) {
        String userInput1 = "查询用户USER1008的订单";
        return args -> {

            var chatClient = chatClientBuilder
                    .defaultToolCallbacks(tools)
                    .build();

            System.out.println("\n>>> QUESTION: " + userInput1);
            System.out.println("\n>>> ASSISTANT: " + chatClient.prompt(userInput1).call().content());


            //context.close();
        };

    }
}