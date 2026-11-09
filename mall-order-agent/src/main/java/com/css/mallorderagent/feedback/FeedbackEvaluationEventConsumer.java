package com.css.mallorderagent.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
@RocketMQMessageListener(topic = "${agent.evaluation.sync.topic:agent-evaluation-events}",
        selectorExpression = "${agent.evaluation.sync.tag:evaluation}",
        consumerGroup = "agent-platform-evaluation-consumer", consumeMode = ConsumeMode.CONCURRENTLY)
public class FeedbackEvaluationEventConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(FeedbackEvaluationEventConsumer.class);
    private final ObjectMapper objectMapper;
    private final FeedbackEvaluationRepository repository;

    public FeedbackEvaluationEventConsumer(ObjectMapper objectMapper, FeedbackEvaluationRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    /**
     * 消费 AgentInsight 评测事件并按 responseId 和聚合版本幂等更新本地结果。
     * 无效消息会抛出异常，交由 RocketMQ 的消费重试策略处理。
     *
     * @param message 评测事件 JSON
     */
    @Override
    public void onMessage(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            JsonNode payload = event.path("payload");
            String responseId = text(payload, "responseId");
            if (responseId == null) throw new IllegalArgumentException("responseId missing");
            repository.upsert(new FeedbackEvaluationRepository.FeedbackEvaluationEvent(
                    responseId, text(payload, "traceId"), text(payload, "evaluationStatus"),
                    event.hasNonNull("aggregateVersion") ? event.get("aggregateVersion").asLong() : 0L,
                    integer(payload, "scoreTotal"), integer(payload, "scoreMax"), bool(payload, "passed"),
                    payload.path("evaluationDetail").isMissingNode() ? null : payload.path("evaluationDetail").toString(),
                    text(payload, "evaluatorVersion"), timestamp(payload, "evaluatedAt")));
        } catch (Exception e) {
            log.error("Unable to consume AgentInsight evaluation event", e);
            throw new IllegalStateException("evaluation event rejected", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
    private static Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }
    private static Boolean bool(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asBoolean() : null;
    }
    private static Timestamp timestamp(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : Timestamp.valueOf(value.replace('T', ' '));
    }
}
