package com.css.mallorderagent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * 基于 testdata/ask/ask-cases.json 对运行中的 Agent 发起真实 ask 请求。
 * <p>
 * 前置：mall-order-agent 已在 8087 启动（可选 mall-order 8081）。
 * 运行：{@code ASK_LIVE_IT=true mvn test -Dtest=OrderAgentAskLiveIT}
 */
@EnabledIfEnvironmentVariable(named = "ASK_LIVE_IT", matches = "true")
class OrderAgentAskLiveIT {

    private static final String BASE_URL = System.getenv().getOrDefault("ASK_BASE_URL", "http://127.0.0.1:8087");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static JsonNode cases;

    @BeforeAll
    static void loadCasesAndCheckHealth() throws Exception {
        try (var in = OrderAgentAskLiveIT.class.getResourceAsStream("/testdata/ask/ask-cases.json")) {
            assertNotNull(in, "missing classpath:/testdata/ask/ask-cases.json");
            cases = MAPPER.readTree(in);
        }
        Assumptions.assumeTrue(isHealthy(), "Agent 未就绪: " + BASE_URL + "/agent/order/health");
    }

    @TestFactory
    Stream<DynamicTest> askCasesFromTestData() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : cases) {
            String name = c.path("name").asText();
            tests.add(dynamicTest(name, () -> runCase(c)));
        }
        return tests.stream();
    }

    private static void runCase(JsonNode c) throws Exception {
        JsonNode request = c.get("request");
        String body = MAPPER.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/agent/order/ask"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), () -> "HTTP failed: " + response.body());

        JsonNode json = MAPPER.readTree(response.body());
        assertEquals(200, json.path("code").asInt(), () -> "business code failed: " + response.body());

        JsonNode data = json.path("data");
        assertFalse(data.isMissingNode() || data.isNull(), "data is null");
        assertNotNull(data.path("answer").asText(null), "answer missing");
        assertFalse(data.path("answer").asText().isBlank(), "answer blank");

        if (c.has("expectPlanStrategy")) {
            assertEquals(c.path("expectPlanStrategy").asText(), data.path("planStrategy").asText(),
                    () -> "planStrategy mismatch, full=" + response.body());
        }
        if (c.path("expectInterrupted").asBoolean(false)) {
            assertTrue(data.path("interrupted").asBoolean(false)
                            || data.path("awaitingUserConfirm").asBoolean(false),
                    () -> "expected interrupt/await confirm, full=" + response.body());
        }

        System.out.printf("[ask-live] %s strategy=%s interrupted=%s answer=%s%n",
                c.path("name").asText(),
                data.path("planStrategy").asText(),
                data.path("interrupted").asBoolean(),
                abbreviate(data.path("answer").asText(), 120));
    }

    private static boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/agent/order/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            JsonNode json = MAPPER.readTree(response.body());
            return json.path("code").asInt() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }
}
