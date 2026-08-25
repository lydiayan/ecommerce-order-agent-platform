package com.css.mallorderagent.feedback;

import com.css.mallorderagent.config.FeedbackProperties;
import com.css.mallorderagent.dto.AgentFeedbackRequest;
import com.css.mallorderagent.dto.BadCaseUpdateRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.example.mallorderobservability.trace.TracePrivacy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(AgentFeedbackService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final Map<BadCaseStatus, Set<BadCaseStatus>> STATUS_TRANSITIONS = Map.of(
            BadCaseStatus.NEW, EnumSet.of(BadCaseStatus.TRIAGED, BadCaseStatus.IGNORED),
            BadCaseStatus.TRIAGED, EnumSet.of(BadCaseStatus.IN_PROGRESS, BadCaseStatus.IGNORED),
            BadCaseStatus.IN_PROGRESS,
            EnumSet.of(BadCaseStatus.TRIAGED, BadCaseStatus.RESOLVED, BadCaseStatus.IGNORED),
            BadCaseStatus.RESOLVED, EnumSet.of(BadCaseStatus.IN_PROGRESS),
            BadCaseStatus.IGNORED, EnumSet.of(BadCaseStatus.NEW));

    private final AgentFeedbackRepository repository;
    private final FeedbackCrypto crypto;
    private final FeedbackSanitizer sanitizer;
    private final FeedbackProperties properties;
    private final ObjectMapper objectMapper;
    private final FeedbackEventOutboxService eventOutbox;

    @Autowired
    public AgentFeedbackService(AgentFeedbackRepository repository, FeedbackCrypto crypto,
                                FeedbackSanitizer sanitizer, FeedbackProperties properties,
                                ObjectMapper objectMapper, FeedbackEventOutboxService eventOutbox) {
        this.repository = repository;
        this.crypto = crypto;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventOutbox = eventOutbox;
    }

    public AgentFeedbackService(AgentFeedbackRepository repository, FeedbackCrypto crypto,
                                FeedbackSanitizer sanitizer, FeedbackProperties properties,
                                ObjectMapper objectMapper) {
        this(repository, crypto, sanitizer, properties, objectMapper, null);
    }

    public void registerResponse(OrderAgentResponse response, long appUserId,
                                 String actorUserId, boolean feedbackAllowed) {
        if (response == null) return;
        response.setFeedbackEnabled(false);
        response.setResponseId(null);
        if (!properties.isEnabled() || !feedbackAllowed
                || response.getAnswer() == null || response.getAnswer().isBlank()) {
            return;
        }

        String responseId = UUID.randomUUID().toString();
        int retentionDays = Math.max(1, Math.min(properties.getRetentionDays(), 3650));
        String operation = joinOperation(response.getOperationLabel(), response.getApprovalReason());
        try {
            repository.insertResponse(new AgentFeedbackRepository.ResponseSnapshotInsert(
                    responseId, appUserId, fingerprint(actorUserId), blankToNull(response.getTraceId()),
                    valueOrDefault(response.getPlanStrategy(), "UNKNOWN"),
                    blankToNull(response.getIntent()), blankToNull(response.getIntentSource()),
                    response.getIntentConfidence(), blankToNull(response.getRuleMatchStatus()),
                    response.isClarificationRequired(),
                    valueOrDefault(properties.getModelName(), "unknown"),
                    valueOrDefault(properties.getAgentVersion(), "unknown"),
                    response.isGrounded(), response.isInterrupted(),
                    crypto.encrypt(sanitizer.sanitize(response.getQuery(), 10_000)),
                    crypto.encrypt(sanitizer.sanitize(response.getAnswer(), 50_000)),
                    encryptSnapshotValue(response.getConversationId(), 256),
                    encryptSnapshotValue(response.getToolSummary(), 20_000),
                    encryptSnapshotValue(operation, 2_000),
                    LocalDateTime.now().plusDays(retentionDays)));
            response.setResponseId(responseId);
            response.setFeedbackEnabled(true);
        } catch (RuntimeException e) {
            log.error("Unable to register agent response for feedback, traceId={}",
                    response.getTraceId(), e);
        }
    }

    @Transactional
    public FeedbackView submit(AgentFeedbackRequest input, long appUserId) {
        ValidatedFeedback feedback = validateFeedback(input);
        requireOwnedResponse(feedback.responseId(), appUserId);
        AgentFeedbackRepository.FeedbackRow previous = repository
                .findFeedback(feedback.responseId(), appUserId).orElse(null);

        String reasonsJson = writeReasons(feedback.reasons());
        String commentCiphertext = feedback.rating() == FeedbackRating.DOWN
                ? encryptSanitized(feedback.comment(), 500) : null;
        AgentFeedbackRepository.FeedbackRow saved = repository.upsertFeedback(
                feedback.responseId(), appUserId, feedback.rating().name(),
                reasonsJson, commentCiphertext);
        repository.insertFeedbackHistory(feedback.responseId(), appUserId,
                previous == null ? "CREATE" : "UPDATE",
                previous != null ? previous.rating() : null, feedback.rating().name(), reasonsJson);

        if (feedback.rating() == FeedbackRating.DOWN) {
            AgentFeedbackRepository.BadCaseIdentity badCase = openBadCase(
                    feedback.responseId(), appUserId, feedback.reasons());
            appendBadCaseEvent(feedback.responseId(), badCase, feedback.reasons(), "DOWN");
        } else {
            ignoreUntriagedBadCase(feedback.responseId(), appUserId, "feedback changed to UP")
                    .ifPresent(after -> appendBadCaseEvent(feedback.responseId(), after,
                            List.of(), "UP"));
        }
        appendFeedbackEvent(feedback.responseId(), saved, feedback.rating().name(), feedback.reasons(),
                previous == null ? "CREATE" : "UPDATE");
        return toFeedbackView(feedback.responseId(), saved);
    }

    @Transactional
    public FeedbackView cancel(String rawResponseId, long appUserId) {
        String responseId = validateResponseId(rawResponseId);
        requireOwnedResponse(responseId, appUserId);
        AgentFeedbackRepository.FeedbackRow previous = repository
                .findFeedback(responseId, appUserId).orElse(null);
        if (previous == null) return new FeedbackView(responseId, null, List.of(), null, null);

        repository.deleteFeedback(responseId, appUserId);
        repository.insertFeedbackHistory(responseId, appUserId, "CANCEL",
                previous.rating(), null, previous.reasonsJson());
        ignoreUntriagedBadCase(responseId, appUserId, "feedback cancelled")
                .ifPresent(after -> appendBadCaseEvent(responseId, after, List.of(), "CANCEL"));
        appendFeedbackEvent(responseId, previous, null, List.of(), "CANCEL");
        return new FeedbackView(responseId, null, List.of(), null, null);
    }

    @Transactional(readOnly = true)
    public FeedbackView find(String rawResponseId, long appUserId) {
        String responseId = validateResponseId(rawResponseId);
        requireOwnedResponse(responseId, appUserId);
        return repository.findFeedback(responseId, appUserId)
                .map(row -> toFeedbackView(responseId, row))
                .orElseGet(() -> new FeedbackView(responseId, null, List.of(), null, null));
    }

    @Transactional(readOnly = true)
    public EvaluationSnapshotView evaluationSnapshot(String rawResponseId) {
        String responseId = validateResponseId(rawResponseId);
        AgentFeedbackRepository.EvaluationSnapshotRow row = repository.findEvaluationSnapshot(responseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回答不存在或已过期"));
        return new EvaluationSnapshotView(
                row.responseId(), row.traceId(), row.planStrategy(), row.intent(), row.intentSource(),
                row.intentConfidence(), row.ruleMatchStatus(), row.clarificationRequired(),
                row.modelName(), row.agentVersion(),
                row.grounded(), row.interrupted(), decrypt(row.queryCiphertext()), decrypt(row.answerCiphertext()),
                decrypt(row.conversationCiphertext()), decrypt(row.toolSummaryCiphertext()),
                decrypt(row.operationCiphertext()), row.rating(), readReasons(row.reasonsJson()),
                decrypt(row.commentCiphertext()), row.badCaseId(), row.badCaseStatus(), row.badCasePriority());
    }

    @Transactional(readOnly = true)
    public List<BadCaseListView> findBadCases(String rawStatus, String rawReason,
                                              String rawStrategy, String rawModelName,
                                              String rawAgentVersion, LocalDate from,
                                              LocalDate to, int requestedLimit) {
        String status = optionalEnum(rawStatus, BadCaseStatus.class);
        String reason = optionalEnum(rawReason, FeedbackReason.class);
        String strategy = normalizeOptional(rawStrategy, 64);
        String modelName = normalizeOptional(rawModelName, 128);
        String agentVersion = normalizeOptional(rawAgentVersion, 64);
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("开始日期不能晚于结束日期");
        }
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return repository.findBadCases(status, reason, strategy, modelName, agentVersion,
                        from != null ? from.atStartOfDay() : null,
                        to != null ? to.plusDays(1).atStartOfDay() : null, limit).stream()
                .map(row -> new BadCaseListView(
                        row.id(), row.responseId(), row.status(), row.priority(), row.category(),
                        row.ownerUsername(), row.fixVersion(), row.traceId(), row.planStrategy(),
                        row.modelName(), row.agentVersion(), readReasons(row.reasonsJson()),
                        row.createdAt(), row.updatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BadCaseDetailView findBadCase(long badCaseId) {
        if (badCaseId <= 0) throw badRequest("bad case ID 不正确");
        AgentFeedbackRepository.BadCaseDetailRow row = repository.findBadCase(badCaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "bad case 不存在"));
        return new BadCaseDetailView(
                row.id(), row.responseId(), row.status(), row.priority(), row.category(),
                row.ownerUsername(), decrypt(row.rootCauseCiphertext()), decrypt(row.resolutionCiphertext()),
                row.fixVersion(), row.traceId(), row.planStrategy(), row.modelName(), row.agentVersion(),
                row.grounded(), row.interrupted(), decrypt(row.queryCiphertext()),
                decrypt(row.answerCiphertext()), decrypt(row.conversationCiphertext()),
                decrypt(row.toolSummaryCiphertext()), decrypt(row.operationCiphertext()),
                readReasons(row.reasonsJson()),
                decrypt(row.commentCiphertext()), row.createdAt(), row.updatedAt());
    }

    @Transactional
    public BadCaseDetailView updateBadCase(long badCaseId, BadCaseUpdateRequest input,
                                           long adminUserId) {
        if (input == null) throw badRequest("更新内容不能为空");
        AgentFeedbackRepository.BadCaseDetailRow current = repository.findBadCase(badCaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "bad case 不存在"));
        BadCaseStatus fromStatus = parseEnum(current.status(), BadCaseStatus.class, "当前状态不正确");
        BadCaseStatus toStatus = parseEnum(input.status(), BadCaseStatus.class, "状态不正确");
        validateTransition(fromStatus, toStatus);

        String category = normalizeOptional(input.category(), 64);
        String owner = normalizeOptional(input.ownerUsername(), 64);
        String fixVersion = normalizeOptional(input.fixVersion(), 64);
        String rootCause = normalizeOptional(input.rootCause(), 2_000);
        String resolution = normalizeOptional(input.resolution(), 2_000);
        if (toStatus == BadCaseStatus.RESOLVED && (resolution == null || resolution.isBlank())) {
            throw badRequest("关闭 bad case 前必须填写处理结论");
        }

        repository.updateBadCase(badCaseId, toStatus.name(), category, owner,
                encryptSanitized(rootCause, 2_000), encryptSanitized(resolution, 2_000), fixVersion);
        if (fromStatus != toStatus) {
            repository.insertBadCaseHistory(badCaseId, adminUserId, fromStatus.name(), toStatus.name(),
                    crypto.encrypt("status updated by administrator"));
        }
        AgentFeedbackRepository.BadCaseIdentity after = repository.findBadCaseIdentity(current.responseId())
                .orElse(new AgentFeedbackRepository.BadCaseIdentity(badCaseId, 0L, toStatus.name()));
        appendBadCaseEvent(current.responseId(), after, List.of(), "ADMIN_UPDATE");
        return findBadCase(badCaseId);
    }

    @Transactional(readOnly = true)
    public FeedbackMetricsView metrics(int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 365));
        AgentFeedbackRepository.MetricsRow row = repository.metrics(days);
        double participationRate = ratio(row.feedbackCount(), row.responseCount());
        double downRate = ratio(row.downCount(), row.feedbackCount());
        double resolvedRate = ratio(row.resolvedCount(), row.downCount());
        return new FeedbackMetricsView(days, row.responseCount(), row.feedbackCount(), row.upCount(),
                row.downCount(), row.triagedCount(), row.resolvedCount(),
                participationRate, downRate, resolvedRate);
    }

    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void rollupAndPurgeExpiredSnapshots() {
        repository.rollupDailyMetrics();
        int total = 0;
        int deleted;
        do {
            deleted = repository.purgeExpiredResponses();
            total += deleted;
        } while (deleted == 1000);
        if (total > 0) log.info("Purged {} expired agent feedback snapshots", total);
    }

    private AgentFeedbackRepository.BadCaseIdentity openBadCase(String responseId, long appUserId,
                                                                 List<FeedbackReason> reasons) {
        boolean urgent = reasons.contains(FeedbackReason.SAFETY_RISK)
                || reasons.contains(FeedbackReason.TOOL_FAILURE);
        AgentFeedbackRepository.BadCaseIdentity before = repository
                .findBadCaseIdentity(responseId).orElse(null);
        AgentFeedbackRepository.BadCaseIdentity current = repository.openBadCase(
                responseId, urgent ? "URGENT" : "NORMAL");
        if (before == null || BadCaseStatus.IGNORED.name().equals(before.status())) {
            repository.insertBadCaseHistory(current.id(), appUserId,
                    before != null ? before.status() : null, BadCaseStatus.NEW.name(),
                    crypto.encrypt("opened from DOWN feedback"));
        }
        if (urgent) {
            log.error("URGENT_AGENT_BAD_CASE badCaseId={}, responseId={}, reasons={}",
                    current.id(), responseId, reasons);
        }
        return current;
    }

    private java.util.Optional<AgentFeedbackRepository.BadCaseIdentity> ignoreUntriagedBadCase(
            String responseId, long appUserId, String details) {
        return repository.ignoreNewBadCase(responseId).map(current -> {
                repository.insertBadCaseHistory(current.id(), appUserId,
                        BadCaseStatus.NEW.name(), BadCaseStatus.IGNORED.name(), crypto.encrypt(details));
                return current;
        });
    }

    private void appendFeedbackEvent(String responseId, AgentFeedbackRepository.FeedbackRow saved,
                                     String rating, List<FeedbackReason> reasons, String action) {
        if (eventOutbox == null) return;
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("responseId", responseId);
        if (rating != null) payload.put("rating", rating);
        payload.put("reasons", reasons.stream().map(Enum::name).toList());
        payload.put("action", action);
        appendIntentMetadata(payload, responseId);
        eventOutbox.append("FeedbackChanged", responseId, Math.max(1, saved.version()),
                repository.findTraceId(responseId), payload);
    }

    private void appendBadCaseEvent(String responseId, AgentFeedbackRepository.BadCaseIdentity badCase,
                                     List<FeedbackReason> reasons, String action) {
        if (eventOutbox == null || badCase == null) return;
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("responseId", responseId);
        payload.put("badCaseId", badCase.id());
        payload.put("status", badCase.status());
        payload.put("reasons", reasons.stream().map(Enum::name).toList());
        payload.put("action", action);
        appendIntentMetadata(payload, responseId);
        eventOutbox.append("BadCaseChanged", String.valueOf(badCase.id()), Math.max(1, badCase.version()),
                repository.findTraceId(responseId), payload);
    }

    private void appendIntentMetadata(Map<String, Object> payload, String responseId) {
        repository.findIntentMetadata(responseId).ifPresent(metadata -> {
            if (metadata.intent() != null) payload.put("intent", metadata.intent());
            if (metadata.intentSource() != null) payload.put("intentSource", metadata.intentSource());
            if (metadata.intentConfidence() != null) {
                payload.put("intentConfidence", metadata.intentConfidence());
            }
            if (metadata.ruleMatchStatus() != null) {
                payload.put("ruleMatchStatus", metadata.ruleMatchStatus());
            }
            payload.put("clarificationRequired", metadata.clarificationRequired());
        });
    }

    private FeedbackView toFeedbackView(String responseId, AgentFeedbackRepository.FeedbackRow row) {
        return new FeedbackView(responseId, row.rating(), readReasons(row.reasonsJson()),
                decrypt(row.commentCiphertext()), row.updatedAt());
    }

    private ValidatedFeedback validateFeedback(AgentFeedbackRequest input) {
        if (input == null) throw badRequest("反馈内容不能为空");
        String responseId = validateResponseId(input.responseId());
        FeedbackRating rating = parseEnum(input.rating(), FeedbackRating.class, "评价只能是 UP 或 DOWN");
        List<FeedbackReason> reasons = new ArrayList<>();
        if (rating == FeedbackRating.DOWN && input.reasons() != null) {
            LinkedHashSet<FeedbackReason> unique = new LinkedHashSet<>();
            for (String reason : input.reasons()) {
                unique.add(parseEnum(reason, FeedbackReason.class, "点踩原因不正确"));
            }
            reasons.addAll(unique);
        }
        String comment = rating == FeedbackRating.DOWN ? normalizeOptional(input.comment(), 500) : null;
        return new ValidatedFeedback(responseId, rating, List.copyOf(reasons), comment);
    }

    private void requireOwnedResponse(String responseId, long appUserId) {
        if (!repository.responseOwnedBy(responseId, appUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "回答不存在或已过期");
        }
    }

    private String validateResponseId(String rawResponseId) {
        if (rawResponseId == null || rawResponseId.isBlank()) throw badRequest("responseId 不能为空");
        try {
            return UUID.fromString(rawResponseId.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw badRequest("responseId 格式不正确");
        }
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw badRequest(message);
        }
    }

    private <E extends Enum<E>> String optionalEnum(String value, Class<E> type) {
        return value == null || value.isBlank() ? null : parseEnum(value, type, "筛选条件不正确").name();
    }

    private static void validateTransition(BadCaseStatus from, BadCaseStatus to) {
        if (from == to) return;
        if (!STATUS_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw badRequest("不允许从 " + from + " 直接流转到 " + to);
        }
    }

    private String writeReasons(List<FeedbackReason> reasons) {
        try {
            return objectMapper.writeValueAsString(reasons.stream().map(Enum::name).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to encode feedback reasons", e);
        }
    }

    private List<String> readReasons(String reasonsJson) {
        if (reasonsJson == null || reasonsJson.isBlank()) return List.of();
        try {
            return List.copyOf(objectMapper.readValue(reasonsJson, STRING_LIST));
        } catch (JsonProcessingException e) {
            log.warn("Unable to parse stored feedback reasons", e);
            return List.of();
        }
    }

    private String encryptSanitized(String value, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        return normalized != null ? crypto.encrypt(sanitizer.sanitize(normalized, maxLength)) : null;
    }

    private String encryptSnapshotValue(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return crypto.encrypt(sanitizer.sanitize(value.trim(), maxLength));
    }

    private String decrypt(String value) {
        return value != null ? crypto.decrypt(value) : null;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw badRequest("字段长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static String joinOperation(String label, String reason) {
        if ((label == null || label.isBlank()) && (reason == null || reason.isBlank())) return null;
        return valueOrDefault(label, "") + "\n" + valueOrDefault(reason, "");
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String fingerprint(String value) {
        return value == null || value.isBlank() ? null : TracePrivacy.fingerprint(value);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0D : Math.round((double) numerator / denominator * 10_000D) / 10_000D;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ValidatedFeedback(String responseId, FeedbackRating rating,
                                     List<FeedbackReason> reasons, String comment) { }

    public record FeedbackView(String responseId, String rating, List<String> reasons,
                               String comment, LocalDateTime updatedAt) { }

    public record EvaluationSnapshotView(
            String responseId, String traceId, String planStrategy, String intent, String intentSource,
            Double intentConfidence, String ruleMatchStatus, boolean clarificationRequired,
            String modelName, String agentVersion,
            boolean grounded, boolean interrupted, String query, String answer, String conversationId,
            String toolSummary, String operation, String rating, List<String> reasons, String comment,
            Long badCaseId, String badCaseStatus, String badCasePriority) { }

    public record BadCaseListView(
            long id, String responseId, String status, String priority, String category,
            String ownerUsername, String fixVersion, String traceId, String planStrategy,
            String modelName, String agentVersion, List<String> reasons,
            LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record BadCaseDetailView(
            long id, String responseId, String status, String priority, String category,
            String ownerUsername, String rootCause, String resolution, String fixVersion,
            String traceId, String planStrategy, String modelName, String agentVersion,
            boolean grounded, boolean interrupted, String query, String answer,
            String conversationId, String toolSummary, String operation,
            List<String> reasons, String comment,
            LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record FeedbackMetricsView(
            int days, long responseCount, long feedbackCount, long upCount, long downCount,
            long triagedCount, long resolvedCount, double participationRate,
            double downRate, double resolvedRate) { }
}
