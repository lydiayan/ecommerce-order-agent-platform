package com.css.mallorderagent.stream;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.dto.OrderAgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求级 SSE 会话注册器。Graph 状态只保存 streamId，不保存 HTTP 连接对象。
 */
@Component
public class AgentStreamSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamSessionRegistry.class);

    private final Map<String, StreamSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger activeStreams = new AtomicInteger();
    private final OrderAgentProperties.StreamingProperties properties;
    private final Clock clock;

    @Autowired
    public AgentStreamSessionRegistry(OrderAgentProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AgentStreamSessionRegistry(OrderAgentProperties properties, Clock clock) {
        this.properties = properties.getStreaming();
        this.clock = clock;
    }

    public StreamHandle open() {
        int active = activeStreams.incrementAndGet();
        if (active > Math.max(1, properties.getMaxActiveStreams())) {
            activeStreams.decrementAndGet();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "流式请求较多，请稍后重试");
        }

        String streamId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Math.max(1_000L, properties.getTimeoutMillis()));
        StreamSession session = new StreamSession(streamId, emitter, clock.millis());
        sessions.put(streamId, session);
        emitter.onCompletion(() -> cancel(streamId));
        emitter.onTimeout(() -> cancel(streamId));
        emitter.onError(error -> cancel(streamId));
        return new StreamHandle(streamId, emitter);
    }

    public void start(String streamId) {
        requireSession(streamId).send("start", Map.of("streamId", streamId));
    }

    public void emitDelta(String streamId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        StreamSession session = requireSession(streamId);
        long sequence = session.nextSequence();
        session.send("delta", Map.of("sequence", sequence, "text", text));
    }

    public Mono<Void> cancellationSignal(String streamId) {
        StreamSession session = sessions.get(streamId);
        return session != null ? session.cancellationSignal() : Mono.empty();
    }

    public boolean isCancelled(String streamId) {
        StreamSession session = sessions.get(streamId);
        return session == null || session.isCancelled();
    }

    public void complete(String streamId, OrderAgentResponse response) {
        StreamSession session = sessions.get(streamId);
        if (session == null) {
            return;
        }
        try {
            session.complete(response);
        } finally {
            remove(streamId, session);
        }
    }

    public void fail(String streamId, StreamError error) {
        StreamSession session = sessions.get(streamId);
        if (session == null) {
            return;
        }
        try {
            session.fail(error);
        } finally {
            remove(streamId, session);
        }
    }

    public void cancel(String streamId) {
        StreamSession session = sessions.get(streamId);
        if (session != null) {
            session.cancel();
        }
    }

    public void release(String streamId) {
        StreamSession session = sessions.get(streamId);
        if (session != null) {
            session.cancel();
            remove(streamId, session);
        }
    }

    public int activeStreamCount() {
        return activeStreams.get();
    }

    @Scheduled(fixedDelayString = "${agent.streaming.cleanup-interval-ms:60000}")
    void expireStaleSessions() {
        long now = clock.millis();
        long timeout = Math.max(1_000L, properties.getTimeoutMillis());
        sessions.forEach((streamId, session) -> {
            if (now - session.createdAtMillis() >= timeout) {
                session.cancel();
                remove(streamId, session);
            }
        });
    }

    @Scheduled(fixedDelayString = "${agent.streaming.heartbeat-interval-ms:15000}")
    void sendHeartbeats() {
        sessions.forEach((streamId, session) -> {
            try {
                session.heartbeat();
            } catch (AgentStreamDisconnectedException e) {
                log.debug("SSE heartbeat detected disconnected stream {}", streamId);
            }
        });
    }

    private StreamSession requireSession(String streamId) {
        StreamSession session = sessions.get(streamId);
        if (session == null || session.isCancelled()) {
            throw new AgentStreamDisconnectedException("SSE stream is no longer active");
        }
        return session;
    }

    private void remove(String streamId, StreamSession session) {
        if (sessions.remove(streamId, session)) {
            activeStreams.decrementAndGet();
        }
    }

    public record StreamHandle(String streamId, SseEmitter emitter) {
    }

    public record StreamError(int code, String message, boolean retryable) {
    }

    private static final class StreamSession {

        private final String streamId;
        private final SseEmitter emitter;
        private final long createdAtMillis;
        private final AtomicLong sequence = new AtomicLong();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final Sinks.Empty<Void> cancellation = Sinks.empty();

        private StreamSession(String streamId, SseEmitter emitter, long createdAtMillis) {
            this.streamId = streamId;
            this.emitter = emitter;
            this.createdAtMillis = createdAtMillis;
        }

        private long createdAtMillis() {
            return createdAtMillis;
        }

        private long nextSequence() {
            return sequence.incrementAndGet();
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private Mono<Void> cancellationSignal() {
            return cancellation.asMono();
        }

        private synchronized void send(String eventName, Object data) {
            if (cancelled.get() || terminal.get()) {
                throw new AgentStreamDisconnectedException("SSE stream is no longer writable");
            }
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                cancel();
                throw new AgentStreamDisconnectedException("SSE client disconnected", e);
            }
        }

        private synchronized void complete(OrderAgentResponse response) {
            if (cancelled.get() || !terminal.compareAndSet(false, true)) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("complete").data(response));
                emitter.complete();
            } catch (IOException | IllegalStateException e) {
                cancel();
                log.debug("Unable to complete SSE stream {}: {}", streamId, e.getMessage());
            }
        }

        private synchronized void heartbeat() {
            if (cancelled.get() || terminal.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                cancel();
                throw new AgentStreamDisconnectedException("SSE client disconnected", e);
            }
        }

        private synchronized void fail(StreamError error) {
            if (cancelled.get() || !terminal.compareAndSet(false, true)) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("error").data(error));
                emitter.complete();
            } catch (IOException | IllegalStateException e) {
                cancel();
                log.debug("Unable to send SSE error for stream {}: {}", streamId, e.getMessage());
            }
        }

        private void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                cancellation.tryEmitEmpty();
            }
        }
    }
}
