package example.mallordercmp_ssoclient.controller;

import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/agent")
public class MallAgentController {

    private static final Logger log = LoggerFactory.getLogger(MallAgentController.class);
    private final ChatClient chatClient;
    private final int MAXMESSAGES=100;
    private final MessageWindowChatMemory messageWindowChatMemory;

    private final ElasticsearchVectorStore vectorStore;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    public MallAgentController(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools, RedisChatMemoryRepository redisChatMemoryRepository,@Qualifier(value = "esVectorStore") ElasticsearchVectorStore vectorStore) {
        this.messageWindowChatMemory= MessageWindowChatMemory.builder().
        chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(MAXMESSAGES).build();
        this.chatClient = chatClientBuilder
                .defaultSystem("用户取消订单，或者创建订单时，需要用户确认，根据用户确认的结果，继续下一流程。通过调用tool返回的保留json格式")
                .defaultToolCallbacks(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build())
                .build();
    this.vectorStore = vectorStore;
        var documentRetriever=VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.90)
                .build();
         retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .build();
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message) {
        //return chatClient.prompt(message).call().content();
        log.info("chat----query:"+message);
        String response=chatClient.prompt(message)
                .advisors(retrievalAugmentationAdvisor)
                .call().content();
                log.info("chat----response:"+response);
        return response;
    }

    @GetMapping("call")
    public String call(@RequestParam(value = "query",defaultValue = "你好，我是商城助手") String query,@RequestParam(value = "conversationId",defaultValue = "zhushou") String conversationId) {
        return chatClient.prompt(query).advisors(a->a.param("CONVERSATIONID", conversationId)).call().content();
    }
}