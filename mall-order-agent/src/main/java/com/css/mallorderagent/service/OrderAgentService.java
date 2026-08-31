package com.css.mallorderagent.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.demo.DemoActorContext;
import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.dto.AbandonConversationRequest;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.HumanFeedbackRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.planner.HumanApprovalDetector.ConfirmationIntent;
import com.css.mallorderagent.planner.PlanResult;
import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 订单 Agent 入口：Planner → ActionRunner 动态调度 → Prompt → LLM → Human → Answer。
 */
@Service
public class OrderAgentService {

    private static final Logger log = LoggerFactory.getLogger(OrderAgentService.class);

    private final CompiledGraph orderAgentCompiledGraph;
    private final HybridMemoryManager hybridMemoryManager;
    private final OrderAgentProperties orderAgentProperties;
    private final RagTraceService ragTraceService;
    private final PendingConfirmationService pendingConfirmationService;
    private final DemoPersonaService demoPersonaService;

    public OrderAgentService(CompiledGraph orderAgentCompiledGraph,
                             HybridMemoryManager hybridMemoryManager,
                             OrderAgentProperties orderAgentProperties,
                             RagTraceService ragTraceService,
                             PendingConfirmationService pendingConfirmationService,
                             DemoPersonaService demoPersonaService) {
        this.orderAgentCompiledGraph = orderAgentCompiledGraph;
        this.hybridMemoryManager = hybridMemoryManager;
        this.orderAgentProperties = orderAgentProperties;
        this.ragTraceService = ragTraceService;
        this.pendingConfirmationService = pendingConfirmationService;
        this.demoPersonaService = demoPersonaService;
    }

    /**
     * 以允许敏感操作确认的默认策略同步执行一次 Agent 问答。
     *
     * @param request 用户问题、会话编号和 RAG 检索参数
     * @param actorUserId 当前业务身份编号，用于限定能力和数据范围
     * @return Agent 回答、规划结果、Trace 信息和可能的人工确认状态
     */
    public OrderAgentResponse ask(AskRequest request, String actorUserId) {
        return ask(request, actorUserId, true);
    }

    /**
     * 按指定安全策略同步执行一次 Agent 问答。
     *
     * @param request 用户问题、会话编号和 RAG 检索参数
     * @param actorUserId 当前业务身份编号
     * @param sensitiveConfirmationAllowed 当前身份是否允许确认或取消敏感业务操作
     * @return Agent 回答、规划结果、Trace 信息和可能的人工确认状态
     */
    public OrderAgentResponse ask(AskRequest request, String actorUserId,
                                  boolean sensitiveConfirmationAllowed) {
        return ask(request, actorUserId, sensitiveConfirmationAllowed, null);
    }

    /**
     * 执行流式 Agent 问答，并将生成增量写入预先注册的 SSE 流会话。
     *
     * @param request 用户问题、会话编号和 RAG 检索参数
     * @param actorUserId 当前业务身份编号
     * @param sensitiveConfirmationAllowed 当前身份是否允许确认或取消敏感业务操作
     * @param streamId 已由流会话注册中心创建的流编号
     * @return 流式生成结束后的完整 Agent 响应
     * @throws IllegalArgumentException streamId 为空时抛出
     */
    public OrderAgentResponse askStreaming(AskRequest request, String actorUserId,
                                           boolean sensitiveConfirmationAllowed, String streamId) {
        if (streamId == null || streamId.isBlank()) {
            throw new IllegalArgumentException("streamId must not be blank");
        }
        return ask(request, actorUserId, sensitiveConfirmationAllowed, streamId);
    }

    private OrderAgentResponse ask(AskRequest request, String actorUserId,
                                   boolean sensitiveConfirmationAllowed, String streamId) {
        validateAsk(request);
        String userId = requireActorUserId(actorUserId);
        sanitizeUntrustedRequest(request);
        String sessionId = resolveSessionId(request);
        String threadId = threadId(userId, sessionId);

        if (pendingConfirmationService.isAwaiting(threadId)) {
            ConfirmationIntent intent = HumanApprovalDetector.parseUserConfirmationIntent(request.getQuery());
            if (!sensitiveConfirmationAllowed && intent != ConfirmationIntent.UNKNOWN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "当前身份不能确认或取消敏感业务操作");
            }
            return handlePendingConfirmationReply(request, userId, sessionId, threadId, streamId);
        }

        if (!ragTraceService.isEnabled()) {
            return askInternal(request, userId, sessionId, threadId, RagTraceScope.noop(), streamId);
        }

        Map<String, Object> attrs = baseTraceAttributes(sessionId, userId);
        attrs.put("queryLength", request.getQuery().trim().length());
        attrs.put("queryFingerprint", TracePrivacy.fingerprint(request.getQuery().trim()));
        attrs.put("streaming", streamId != null);
        try (RagTraceScope trace = ragTraceService.begin("agent.ask", attrs)) {
            OrderAgentResponse response = askInternal(request, userId, sessionId, threadId, trace, streamId);
            response.setTraceId(trace.traceId());
            if (response.getRetrieval() != null) {
                response.getRetrieval().setTraceId(trace.traceId());
            }
            trace.attribute("grounded", response.isGrounded());
            trace.attribute("planStrategy", response.getPlanStrategy());
            addIntentTraceAttributes(trace, response);
            trace.attribute("interrupted", response.isInterrupted());
            trace.attribute("responseLength", response.getAnswer() != null ? response.getAnswer().length() : 0);
            return response;
        }
    }

    /**
     * 使用人工审核结论恢复当前身份拥有的中断 Graph，并建立第二阶段 Trace。
     *
     * @param request Graph 线程编号、是否批准和可选修订问题
     * @param actorUserId 当前业务身份编号，用于校验待确认状态归属
     * @return 恢复后的回答；流程再次中断时仍会携带人工确认信息
     */
    public OrderAgentResponse resume(HumanFeedbackRequest request, String actorUserId) {
        validateResume(request);
        String threadId = request.getThreadId().trim();
        PendingConfirmationService.PendingConfirmation pending = pendingConfirmationService.find(threadId)
                .filter(value -> value.userId().equals(actorUserId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!ragTraceService.isEnabled()) {
            return resumeInternal(request, threadId, RagTraceScope.noop());
        }

        Map<String, Object> attrs = baseTraceAttributes(threadId, pending.userId());
        attrs.put("approved", Boolean.TRUE.equals(request.getApproved()));
        if (pending.sourceTraceId() != null) {
            attrs.put("continuedFromTraceId", pending.sourceTraceId());
        }
        try (RagTraceScope trace = ragTraceService.begin("agent.resume", attrs)) {
            OrderAgentResponse response = resumeInternal(request, threadId, trace);
            response.setTraceId(trace.traceId());
            trace.attribute("grounded", response.isGrounded());
            trace.attribute("planStrategy", response.getPlanStrategy());
            addIntentTraceAttributes(trace, response);
            trace.attribute("interrupted", response.isInterrupted());
            trace.attribute("responseLength", response.getAnswer() != null ? response.getAnswer().length() : 0);
            return response;
        }
    }

    /**
     * 放弃当前身份拥有的待确认操作，并清除对应会话的等待状态。
     *
     * @param request 包含 Graph 线程编号的放弃请求
     * @param actorUserId 当前业务身份编号，用于校验待确认状态归属
     * @return 找到并清除待确认状态后返回 true
     */
    public boolean abandon(AbandonConversationRequest request, String actorUserId) {
        if (request == null || request.getThreadId() == null || request.getThreadId().isBlank()) {
            throw new IllegalArgumentException("threadId must not be blank");
        }
        PendingConfirmationService.PendingConfirmation pending = pendingConfirmationService.find(request.getThreadId())
                .filter(value -> value.userId().equals(actorUserId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        pendingConfirmationService.clear(pending.conversationId());
        return true;
    }

    private OrderAgentResponse resumeInternal(HumanFeedbackRequest request, String threadId, RagTraceScope trace) {
        Map<String, Object> feedback = buildHumanFeedback(request);

        RunnableConfig resumeConfig = RunnableConfig.builder()
                .threadId(threadId)
                .resume()
                .addStateUpdate(Map.of(
                        AgentGraphKeys.HUMAN_FEEDBACK, feedback,
                        AgentGraphKeys.STREAM_ID, ""))
                .build();

        log.info("Resume graph, threadId={}, approved={}, revised={}",
                threadId, request.getApproved(), request.getRevisedQuery() != null);

        RagTracingAdvisor.bindParentScope(trace);
        try {
            GraphRunResult runResult = runGraph(Map.of(), resumeConfig);
            OrderAgentResponse response = toResponse(runResult.state(), runResult.interrupted(), threadId);
            if (runResult.interrupted()) {
                response.setInterruptMessage(readInterruptMessage(runResult.output()));
            } else {
                pendingConfirmationService.clear(threadId);
            }
            return response;
        } finally {
            RagTracingAdvisor.clearParentScope();
        }
    }

    private OrderAgentResponse askInternal(AskRequest request, String userId, String sessionId,
                                           String threadId, RagTraceScope trace, String streamId) {
        Map<String, Object> inputs = buildGraphInputs(request, userId, sessionId, streamId);

        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        RagTracingAdvisor.bindParentScope(trace);
        try {
            GraphRunResult runResult = runGraph(inputs, config);
            OrderAgentResponse response = toResponse(runResult.state(), runResult.interrupted(), threadId);
            if (runResult.interrupted()) {
                response.setInterruptMessage(readInterruptMessage(runResult.output()));
                log.info("Graph interrupted before human review, threadId={}, answerLength={}",
                        threadId, response.getAnswer() != null ? response.getAnswer().length() : 0);
                finalizeAwaitingConfirmation(
                        response, userId, sessionId, threadId, request.getQuery().trim(), trace.traceId());
            } else {
                pendingConfirmationService.clear(threadId);
            }
            return response;
        } finally {
            RagTracingAdvisor.clearParentScope();
        }
    }

    private GraphRunResult runGraph(Map<String, Object> inputs, RunnableConfig config) {
        NodeOutput output = orderAgentCompiledGraph.invokeAndGetOutput(inputs, config)
                .orElseThrow(() -> new IllegalStateException("Order agent graph returned empty output"));
        OverAllState state = output.state();
        boolean interrupted = output instanceof InterruptionMetadata;
        return new GraphRunResult(output, state, interrupted);
    }

    private Map<String, Object> buildGraphInputs(AskRequest request, String userId, String sessionId,
                                                  String streamId) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(AgentGraphKeys.ASK_REQUEST, request);
        inputs.put(AgentGraphKeys.QUERY, request.getQuery().trim());
        inputs.put(AgentGraphKeys.USER_ID, userId);
        inputs.put(AgentGraphKeys.SESSION_ID, sessionId);
        inputs.put(AgentGraphKeys.HUMAN_REVIEW_ENABLED, orderAgentProperties.getGraph().isHumanReviewEnabled());
        DemoActorContext actor = demoPersonaService.resolveActor(userId);
        inputs.put(AgentGraphKeys.PERSONA_CONTEXT, actor.personaPrompt());
        inputs.put(AgentGraphKeys.CAPABILITIES,
                actor.capabilities().stream().map(Enum::name).toList());
        inputs.put(AgentGraphKeys.AUTHORIZED_CUSTOMER_IDS, actor.authorizedCustomerIds());
        inputs.put(AgentGraphKeys.RAG_ROLE_SCOPES, actor.roleScopes());
        inputs.put(AgentGraphKeys.RAG_DEPARTMENT_SCOPES, actor.departmentScopes());
        inputs.put(AgentGraphKeys.STREAM_ID, streamId != null ? streamId : "");
        return inputs;
    }

    private OrderAgentResponse toResponse(OverAllState state, boolean interrupted, String threadId) {
        String query = state.value(AgentGraphKeys.QUERY, "");
        String sessionId = state.value(AgentGraphKeys.SESSION_ID, AgentGraphSupport.DEFAULT_SESSION_ID);
        String answer = state.value(AgentGraphKeys.ANSWER, "");
        boolean grounded = state.value(AgentGraphKeys.GROUNDED, false);
        String planStrategy = state.value(AgentGraphKeys.PLAN_STRATEGY, "RAG_QA");
        PlanResult plan = state.value(AgentGraphKeys.PLAN, PlanResult.class).orElse(null);
        SearchResponse retrieval = plan != null && plan.needRag()
                ? state.value(AgentGraphKeys.RETRIEVAL, SearchResponse.class).orElse(null)
                : null;

        OrderAgentResponse response = new OrderAgentResponse();
        response.setQuery(query);
        response.setConversationId(sessionId);
        response.setAnswer(answer);
        response.setGrounded(grounded);
        response.setPlanStrategy(planStrategy);
        response.setIntent(state.value(AgentGraphKeys.INTENT, ""));
        response.setIntentSource(state.value(AgentGraphKeys.INTENT_SOURCE, ""));
        response.setIntentConfidence(state.value(AgentGraphKeys.INTENT_CONFIDENCE, 0D));
        response.setRuleMatchStatus(state.value(AgentGraphKeys.RULE_MATCH_STATUS, ""));
        response.setClarificationRequired(state.value(AgentGraphKeys.CLARIFICATION_REQUIRED, false));
        response.setRetrieval(retrieval);
        response.setToolSummary(state.value(AgentGraphKeys.TOOL_RESULT, ""));
        response.setInterrupted(interrupted);
        response.setThreadId(threadId);
        response.setApprovalReason(state.value(AgentGraphKeys.APPROVAL_REASON, ""));
        response.setOperationLabel(HumanApprovalDetector.resolveOperationLabel(query));
        return response;
    }

    private static void addIntentTraceAttributes(RagTraceScope trace, OrderAgentResponse response) {
        trace.attribute("intent", response.getIntent() != null ? response.getIntent() : "");
        trace.attribute("intentSource",
                response.getIntentSource() != null ? response.getIntentSource() : "");
        trace.attribute("intentConfidence", response.getIntentConfidence());
        trace.attribute("ruleMatchStatus",
                response.getRuleMatchStatus() != null ? response.getRuleMatchStatus() : "");
        trace.attribute("clarificationRequired", response.isClarificationRequired());
    }

    private OrderAgentResponse handlePendingConfirmationReply(AskRequest request, String userId,
                                                               String sessionId, String threadId,
                                                               String streamId) {
        ConfirmationIntent intent = HumanApprovalDetector.parseUserConfirmationIntent(request.getQuery());
        if (intent == ConfirmationIntent.CONFIRM) {
            HumanFeedbackRequest resumeRequest = new HumanFeedbackRequest();
            resumeRequest.setThreadId(threadId);
            resumeRequest.setApproved(true);
            return resume(resumeRequest, userId);
        }
        if (intent == ConfirmationIntent.CANCEL) {
            HumanFeedbackRequest resumeRequest = new HumanFeedbackRequest();
            resumeRequest.setThreadId(threadId);
            resumeRequest.setApproved(false);
            return resume(resumeRequest, userId);
        }

        pendingConfirmationService.clear(threadId);
        log.info("Pending confirmation abandoned, sessionId={}, newQuery='{}'", sessionId, request.getQuery());
        return askInternal(request, userId, sessionId, threadId, RagTraceScope.noop(), streamId);
    }

    private void finalizeAwaitingConfirmation(OrderAgentResponse response,
                                              String userId,
                                              String sessionId,
                                              String threadId,
                                              String userQuery,
                                              String traceId) {
        if (!response.isInterrupted()
                || !HumanApprovalDetector.isDangerousOrderOp(response.getPlanStrategy())) {
            return;
        }
        response.setAwaitingUserConfirm(true);
        pendingConfirmationService.markAwaiting(threadId, userId, traceId);
        if (response.getAnswer() != null && !response.getAnswer().isBlank()) {
            hybridMemoryManager.addExchange(userId, sessionId, userQuery, response.getAnswer());
        }
    }

    private static String readInterruptMessage(NodeOutput output) {
        if (!(output instanceof InterruptionMetadata metadata)) {
            return "请审核助手回答";
        }
        return metadata.metadata("message").map(String::valueOf).orElse("请审核助手回答");
    }

    private static Map<String, Object> buildHumanFeedback(HumanFeedbackRequest request) {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("approved", Boolean.TRUE.equals(request.getApproved()));
        if (request.getRevisedQuery() != null && !request.getRevisedQuery().isBlank()) {
            feedback.put("revisedQuery", request.getRevisedQuery().trim());
        }
        return feedback;
    }

    private static void validateAsk(AskRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
    }

    private static void validateResume(HumanFeedbackRequest request) {
        if (request == null || request.getThreadId() == null || request.getThreadId().isBlank()) {
            throw new IllegalArgumentException("threadId must not be blank");
        }
        if (request.getApproved() == null) {
            throw new IllegalArgumentException("approved must not be null");
        }
    }

    private static String resolveSessionId(AskRequest request) {
        if (request.getConversationId() == null || request.getConversationId().isBlank()) {
            return UUID.randomUUID().toString();
        }
        String value = request.getConversationId().trim();
        if (value.length() > 128 || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("conversationId format is invalid");
        }
        return value;
    }

    private static String requireActorUserId(String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "authenticated actor is required");
        }
        return actorUserId.trim();
    }

    static void sanitizeUntrustedRequest(AskRequest request) {
        request.setUserId(null);
        request.setActorUserId(null);
        request.setSourceFilter(null);
        request.setDepartmentFilter(null);
        request.setRoleFilter(null);
        request.setVersionFilter(null);
        request.setTopK(Math.min(Math.max(request.getTopK(), 1), 10));
    }

    private static String threadId(String userId, String sessionId) {
        return userId + "::" + sessionId;
    }

    private static Map<String, Object> baseTraceAttributes(String conversationId, String userId) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("traceSchemaVersion", "1.0");
        attrs.put("agentName", "mall-order-agent");
        attrs.put("agentVersion", "1.0.0");
        attrs.put("conversationId", conversationId);
        attrs.put("userFingerprint", TracePrivacy.fingerprint(userId));
        return attrs;
    }

    private record GraphRunResult(NodeOutput output, OverAllState state, boolean interrupted) {
    }
}
