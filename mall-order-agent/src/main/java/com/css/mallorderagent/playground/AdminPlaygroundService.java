package com.css.mallorderagent.playground;

import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.demo.DemoPersonaView;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.css.mallorderagent.memory.ConversationTurn;
import com.css.mallorderagent.service.OrderAgentService;
import com.example.mallordermilvusrag.dto.SearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * 管理员 Agent 调试台。会话历史只保存在当前进程内，且不会进入业务用户记忆或反馈统计。
 */
@Service
public class AdminPlaygroundService {

    private static final Pattern CONVERSATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final int MAX_SESSIONS = 200;
    private static final int MAX_HISTORY_TURNS = 10;
    private static final int MAX_QUERY_LENGTH = 4_000;

    private final OrderAgentService orderAgentService;
    private final DemoPersonaService identities;
    private final ConcurrentMap<SessionKey, SessionState> sessions = new ConcurrentHashMap<>();

    public AdminPlaygroundService(OrderAgentService orderAgentService, DemoPersonaService identities) {
        this.orderAgentService = orderAgentService;
        this.identities = identities;
    }

    public PlaygroundResponse ask(long adminUserId, String actorUserId, String query,
                                  String conversationId, Integer topK,
                                  boolean webSearchEnabled) {
        requireAdminUserId(adminUserId);
        String normalizedActor = requireText(actorUserId, "actorUserId");
        String normalizedQuery = requireText(query, "query");
        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "query must contain at most " + MAX_QUERY_LENGTH + " characters");
        }
        String normalizedConversation = requireConversationId(conversationId);
        DemoPersonaView identity = identities.requirePersona(normalizedActor);
        SessionKey key = new SessionKey(adminUserId, normalizedConversation);

        purgeExpiredSessions();
        makeRoomFor(key);
        SessionState session = sessions.compute(key, (ignored, current) -> {
            if (current == null || current.expired()) {
                return new SessionState(identity.actorUserId());
            }
            if (!current.actorUserId.equals(identity.actorUserId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "conversation identity cannot be changed; start a new session");
            }
            return current;
        });

        synchronized (session) {
            if (sessions.get(key) != session) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "conversation was reset; start a new session");
            }
            session.touch();
            AskRequest request = new AskRequest();
            request.setQuery(normalizedQuery);
            request.setConversationId(normalizedConversation);
            request.setTopK(topK != null ? Math.max(1, Math.min(topK, 10)) : 5);

            OrderAgentResponse response = orderAgentService.askForPlayground(
                    request, identity.actorUserId(), session.history(), adminUserId,
                    webSearchEnabled);
            if (!response.isInterrupted() && response.getAnswer() != null
                    && !response.getAnswer().isBlank()) {
                session.append(new ConversationTurn(normalizedQuery, response.getAnswer()));
            } else {
                session.touch();
            }
            return PlaygroundResponse.from(identity, response, webSearchEnabled);
        }
    }

    public boolean reset(long adminUserId, String actorUserId, String conversationId) {
        requireAdminUserId(adminUserId);
        String normalizedActor = requireText(actorUserId, "actorUserId");
        String normalizedConversation = requireConversationId(conversationId);
        identities.requirePersona(normalizedActor);
        SessionKey key = new SessionKey(adminUserId, normalizedConversation);
        SessionState session = sessions.get(key);
        boolean removed = false;
        if (session != null) {
            synchronized (session) {
                if (!session.actorUserId.equals(normalizedActor)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "conversation belongs to a different identity");
                }
                removed = sessions.remove(key, session);
            }
        }
        orderAgentService.clearPlaygroundSession(
                normalizedActor, normalizedConversation, adminUserId);
        return removed;
    }

    int sessionCount() {
        return sessions.size();
    }

    private void purgeExpiredSessions() {
        sessions.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    private void makeRoomFor(SessionKey requested) {
        if (sessions.containsKey(requested) || sessions.size() < MAX_SESSIONS) {
            return;
        }
        sessions.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().updatedAt))
                .ifPresent(entry -> sessions.remove(entry.getKey(), entry.getValue()));
    }

    private static void requireAdminUserId(long adminUserId) {
        if (adminUserId <= 0) {
            throw new IllegalArgumentException("adminUserId must be positive");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireConversationId(String value) {
        String normalized = requireText(value, "conversationId");
        if (!CONVERSATION_ID.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "conversationId format is invalid");
        }
        return normalized;
    }

    private record SessionKey(long adminUserId, String conversationId) {
    }

    private static final class SessionState {
        private final String actorUserId;
        private final List<ConversationTurn> turns = new ArrayList<>();
        private Instant updatedAt = Instant.now();

        private SessionState(String actorUserId) {
            this.actorUserId = actorUserId;
        }

        private List<ConversationTurn> history() {
            return List.copyOf(turns);
        }

        private void append(ConversationTurn turn) {
            turns.add(turn);
            while (turns.size() > MAX_HISTORY_TURNS) {
                turns.remove(0);
            }
            touch();
        }

        private void touch() {
            updatedAt = Instant.now();
        }

        private boolean expired() {
            return updatedAt.plus(SESSION_TTL).isBefore(Instant.now());
        }
    }

    public record SkillDiagnostic(String name, Long versionId, String packageDigest) {
    }

    public record PlaygroundResponse(
            String actorUserId,
            String displayName,
            String conversationId,
            String answer,
            boolean grounded,
            String traceId,
            String planStrategy,
            String intent,
            String intentSource,
            double intentConfidence,
            String ruleMatchStatus,
            boolean clarificationRequired,
            SkillDiagnostic skill,
            String toolSummary,
            SearchResponse retrieval,
            boolean webSearchEnabled,
            boolean sensitiveOperationBlocked,
            String operationLabel,
            String approvalReason) {

        private static PlaygroundResponse from(DemoPersonaView identity, OrderAgentResponse response,
                                               boolean webSearchEnabled) {
            SkillDiagnostic skill = response.getSelectedSkillName() != null
                    && !response.getSelectedSkillName().isBlank()
                    ? new SkillDiagnostic(response.getSelectedSkillName(),
                    response.getSelectedSkillVersionId(), response.getSelectedSkillDigest())
                    : null;
            return new PlaygroundResponse(
                    identity.actorUserId(), identity.displayName(), response.getConversationId(),
                    response.getAnswer(), response.isGrounded(), response.getTraceId(),
                    response.getPlanStrategy(), response.getIntent(), response.getIntentSource(),
                    response.getIntentConfidence(), response.getRuleMatchStatus(),
                    response.isClarificationRequired(), skill, response.getToolSummary(),
                    response.getRetrieval(), webSearchEnabled, response.isInterrupted(), response.getOperationLabel(),
                    response.getApprovalReason());
        }
    }
}
