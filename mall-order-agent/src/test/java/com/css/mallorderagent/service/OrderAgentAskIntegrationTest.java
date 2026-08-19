package com.css.mallorderagent.service;

import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * 使用 testdata/ask/ask-cases.json 真实跑通 OrderAgentService.ask（Graph + RAG/LLM）。
 * <p>
 * 依赖：DashScope API Key、Redis、Milvus、MySQL；mall-order(8081) 建议已启动（订单查询用例）。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.mcp.client.enabled=false",
        "agent.order.mcp.enabled=false",
        "observability.producer.enabled=false",
        "observability.consumer.enabled=false",
        "memory.consolidation.enabled=false",
        "memory.user-profile.enabled=false"
})
class OrderAgentAskIntegrationTest {

    @Autowired
    private OrderAgentService orderAgentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestConfiguration
    static class AskTestStubConfig {
        @Bean
        @Primary
        ToolCallbackProvider emptyToolCallbackProvider() {
            return () -> new ToolCallback[0];
        }
    }

    @TestFactory
    Stream<DynamicTest> askWithGeneratedTestData() throws Exception {
        JsonNode cases;
        try (var in = getClass().getResourceAsStream("/testdata/ask/ask-cases.json")) {
            assertNotNull(in, "missing classpath:/testdata/ask/ask-cases.json");
            cases = objectMapper.readTree(in);
        }

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : cases) {
            String name = c.path("name").asText();
            tests.add(dynamicTest(name, () -> runAskCase(c)));
        }
        return tests.stream();
    }

    private void runAskCase(JsonNode c) throws Exception {
        AskRequest request = objectMapper.treeToValue(c.get("request"), AskRequest.class);
        String actorUserId = request.getActorUserId() != null
                ? request.getActorUserId() : request.getUserId();
        OrderAgentResponse response = orderAgentService.ask(request,
                actorUserId != null ? actorUserId : "USER1001");

        assertNotNull(response);
        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isBlank(), "answer should not be blank");

        if (c.has("expectPlanStrategy")) {
            assertEquals(c.path("expectPlanStrategy").asText(), response.getPlanStrategy(),
                    "planStrategy mismatch for case=" + c.path("name").asText());
        }
        if (c.path("expectInterrupted").asBoolean(false)) {
            assertTrue(response.isInterrupted() || response.isAwaitingUserConfirm(),
                    "expected interrupt/await confirm for case=" + c.path("name").asText()
                            + ", answer=" + response.getAnswer());
        }

        System.out.printf("[ask-it] %s strategy=%s grounded=%s interrupted=%s answer=%s%n",
                c.path("name").asText(),
                response.getPlanStrategy(),
                response.isGrounded(),
                response.isInterrupted(),
                abbreviate(response.getAnswer(), 160));
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }
}
