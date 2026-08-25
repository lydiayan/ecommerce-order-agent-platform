package com.css.mallorderagent.feedback;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "agent.feedback.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeedbackEventOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(FeedbackEventOutboxPublisher.class);
    private final FeedbackEventOutboxRepository repository;
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${agent.feedback.sync.topic:agent-feedback-events}")
    private String topic;
    @Value("${agent.feedback.sync.tag:feedback}")
    private String tag;
    @Value("${agent.feedback.sync.batch-size:50}")
    private int batchSize;
    @Value("${agent.feedback.sync.max-attempts:8}")
    private int maxAttempts;

    public FeedbackEventOutboxPublisher(FeedbackEventOutboxRepository repository,
                                        RocketMQTemplate rocketMQTemplate) {
        this.repository = repository;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Scheduled(fixedDelayString = "${agent.feedback.sync.poll-interval-ms:1000}")
    public void publishReadyEvents() {
        for (FeedbackEventOutboxRepository.OutboxRow row : repository.findReady(Math.max(1, batchSize))) {
            try {
                rocketMQTemplate.syncSend(topic + ":" + tag, row.payloadJson(), 3000);
                repository.markPublished(row.id());
            } catch (Exception e) {
                int attempts = row.attempts() + 1;
                boolean dead = attempts >= Math.max(1, maxAttempts);
                long backoffSeconds = Math.min(300, 1L << Math.min(attempts, 8));
                repository.markFailed(row.id(), attempts, LocalDateTime.now().plusSeconds(backoffSeconds),
                        e.getMessage(), dead);
                log.warn("feedback event publish failed eventId={} attempts={} dead={}",
                        row.eventId(), attempts, dead, e);
            }
        }
    }
}
