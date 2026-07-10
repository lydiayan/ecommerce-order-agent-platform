package com.css.mallorderagent.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.HumanFeedbackRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.planner.HumanApprovalDetector.ConfirmationIntent;
import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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

    public OrderAgentService(CompiledGraph orderAgentCompiledGraph,
                             HybridMemoryManager hybridMemoryManager,
                             OrderAgentProperties orderAgentProperties,
                             RagTraceService ragTraceService,
                             PendingConfirmationService pendingConfirmationService) {
        this.orderAgentCompiledGraph = orderAgentCompiledGraph;
        this.hybridMemoryManager = hybridMemoryManager;
        this.orderAgentProperties = orderAgentProperties;
        this.ragTraceService = ragTraceService;
        this.pendingConfirmationService = pendingConfirmationService;
    }

    public OrderAgentResponse ask(AskRequest request) {
        validateAsk(request);
        String userId = resolveUserId(request);
        String sessionId = resolveSessionId(request);

        if (pendingConfirmationService.isAwaiting(sessionId)) {
            return handlePendingConfirmationReply(request, userId, sessionId);
        }

        if (!ragTraceService.isEnabled()) {
            return askInternal(request, userId, sessionId, RagTraceScope.noop());
        }

        Map<String, Object> attrs = Map.of(
                "query", request.getQuery(),
                "userId", userId,
                "sessionId", sessionId);
        try (RagTraceScope trace = ragTraceService.begin("agent.ask", attrs)) {
            OrderAgentResponse response = askInternal(request, userId, sessionId, trace);
            response.setTraceId(trace.traceId());
            if (response.getRetrieval() != null) {
                response.getRetrieval().setTraceId(trace.traceId());
            }
            trace.attribute("grounded", response.isGrounded());
            trace.attribute("planStrategy", response.getPlanStrategy());
            trace.attribute("interrupted", response.isInterrupted());
            return response;
        }
    }

    public OrderAgentResponse resume(HumanFeedbackRequest request) {
        validateResume(request);
        String threadId = request.getThreadId().trim();
        pendingConfirmationService.clear(threadId);
        Map<String, Object> feedback = buildHumanFeedback(request);

        RunnableConfig resumeConfig = RunnableConfig.builder()
                .threadId(threadId)
                .resume()
                .addStateUpdate(Map.of(AgentGraphKeys.HUMAN_FEEDBACK, feedback))
                .build();

        log.info("Resume graph, threadId={}, approved={}, revised={}",
                threadId, request.getApproved(), request.getRevisedQuery() != null);

        GraphRunResult runResult = runGraph(Map.of(), resumeConfig);
        OrderAgentResponse response = toResponse(runResult.state(), runResult.interrupted(), threadId);
        if (runResult.interrupted()) {
            response.setInterruptMessage(readInterruptMessage(runResult.output()));
        }
        return response;
    }

    private OrderAgentResponse askInternal(AskRequest request, String userId, String sessionId, RagTraceScope trace) {
        Map<String, Object> inputs = buildGraphInputs(request, userId, sessionId);

        String threadId = sessionId;
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        RagTracingAdvisor.bindParentScope(trace);
        try {
            GraphRunResult runResult = runGraph(inputs, config);
            OrderAgentResponse response = toResponse(runResult.state(), runResult.interrupted(), threadId);
            if (runResult.interrupted()) {
                response.setInterruptMessage(readInterruptMessage(runResult.output()));
                log.info("Graph interrupted before human review, threadId={}, answerLength={}",
                        threadId, response.getAnswer() != null ? response.getAnswer().length() : 0);
                finalizeAwaitingConfirmation(response, userId, sessionId, request.getQuery().trim());
            } else {
                pendingConfirmationService.clear(sessionId);
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

    private Map<String, Object> buildGraphInputs(AskRequest request, String userId, String sessionId) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(AgentGraphKeys.ASK_REQUEST, request);
        inputs.put(AgentGraphKeys.QUERY, request.getQuery().trim());
        inputs.put(AgentGraphKeys.USER_ID, userId);
        inputs.put(AgentGraphKeys.SESSION_ID, sessionId);
        inputs.put(AgentGraphKeys.HUMAN_REVIEW_ENABLED, orderAgentProperties.getGraph().isHumanReviewEnabled());
        return inputs;
    }

    private OrderAgentResponse toResponse(OverAllState state, boolean interrupted, String threadId) {
        String query = state.value(AgentGraphKeys.QUERY, "");
        String sessionId = state.value(AgentGraphKeys.SESSION_ID, AgentGraphSupport.DEFAULT_SESSION_ID);
        String answer = state.value(AgentGraphKeys.ANSWER, "");
        boolean grounded = state.value(AgentGraphKeys.GROUNDED, false);
        String planStrategy = state.value(AgentGraphKeys.PLAN_STRATEGY, "RAG_QA");
        SearchResponse retrieval = state.value(AgentGraphKeys.RETRIEVAL, SearchResponse.class).orElse(null);

        OrderAgentResponse response = new OrderAgentResponse();
        response.setQuery(query);
        response.setConversationId(sessionId);
        response.setAnswer(answer);
        response.setGrounded(grounded);
        response.setPlanStrategy(planStrategy);
        response.setRetrieval(retrieval);
        response.setInterrupted(interrupted);
        response.setThreadId(threadId);
        response.setApprovalReason(state.value(AgentGraphKeys.APPROVAL_REASON, ""));
        response.setOperationLabel(HumanApprovalDetector.resolveOperationLabel(query));
        return response;
    }

    private OrderAgentResponse handlePendingConfirmationReply(AskRequest request, String userId, String sessionId) {
        ConfirmationIntent intent = HumanApprovalDetector.parseUserConfirmationIntent(request.getQuery());
        if (intent == ConfirmationIntent.CONFIRM) {
            HumanFeedbackRequest resumeRequest = new HumanFeedbackRequest();
            resumeRequest.setThreadId(sessionId);
            resumeRequest.setApproved(true);
            return resume(resumeRequest);
        }
        if (intent == ConfirmationIntent.CANCEL) {
            HumanFeedbackRequest resumeRequest = new HumanFeedbackRequest();
            resumeRequest.setThreadId(sessionId);
            resumeRequest.setApproved(false);
            return resume(resumeRequest);
        }

        pendingConfirmationService.clear(sessionId);
        log.info("Pending confirmation abandoned, sessionId={}, newQuery='{}'", sessionId, request.getQuery());
        return askInternal(request, userId, sessionId, RagTraceScope.noop());
    }

    private void finalizeAwaitingConfirmation(OrderAgentResponse response,
                                              String userId,
                                              String sessionId,
                                              String userQuery) {
        if (!response.isInterrupted()
                || !HumanApprovalDetector.isDangerousOrderOp(response.getPlanStrategy())) {
            return;
        }
        response.setAwaitingUserConfirm(true);
        pendingConfirmationService.markAwaiting(sessionId);
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
            return AgentGraphSupport.DEFAULT_SESSION_ID;
        }
        return request.getConversationId().trim();
    }

    private String resolveUserId(AskRequest request) {
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            return request.getUserId().trim();
        }
        return hybridMemoryManager.getDefaultUserId();
    }

    private record GraphRunResult(NodeOutput output, OverAllState state, boolean interrupted) {
    }
}
