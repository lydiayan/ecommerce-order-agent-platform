package com.css.mallorderagent.controller;

import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.AbandonConversationRequest;
import com.css.mallorderagent.dto.HumanFeedbackRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.css.mallorderagent.service.OrderAgentService;
import com.css.mallorderagent.feedback.AgentFeedbackService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.css.mallorderagent.security.ImpersonationService;
import com.css.mallorderagent.stream.AgentStreamDisconnectedException;
import com.css.mallorderagent.stream.AgentStreamSessionRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.example.mallordermilvusrag.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 订单 Agent HTTP 接口。
 */
@RestController
@RequestMapping("/agent/order")
public class OrderAgentController {

    private static final Logger log = LoggerFactory.getLogger(OrderAgentController.class);

    private final OrderAgentService orderAgentService;
    private final AgentFeedbackService feedbackService;
    private final AgentStreamSessionRegistry streamRegistry;
    private final AsyncTaskExecutor streamExecutor;

    public OrderAgentController(OrderAgentService orderAgentService,
                                AgentFeedbackService feedbackService,
                                AgentStreamSessionRegistry streamRegistry,
                                @Qualifier("agentStreamExecutor") AsyncTaskExecutor streamExecutor) {
        this.orderAgentService = orderAgentService;
        this.feedbackService = feedbackService;
        this.streamRegistry = streamRegistry;
        this.streamExecutor = streamExecutor;
    }

    /**
     * 检查订单 Agent HTTP 服务是否已经启动并可接收请求。
     *
     * @return 固定的服务运行状态说明
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Order agent is running");
    }

    /**
     * 同步执行订单 Agent 问答，依次使用记忆、规划、RAG、工具和 LLM 完成回答。
     *
     * @param request 用户问题、会话编号和检索参数
     * @param principal 当前登录身份；其业务身份决定可访问的订单和能力
     * @return 完整的 Agent 回答、会话状态以及可能的人工确认信息
     */
    @PostMapping("/ask")
    public ApiResponse<OrderAgentResponse> ask(@RequestBody AskRequest request,
                                               @AuthenticationPrincipal SecurityUserPrincipal principal) {
        log.info("POST /agent/order/ask - queryLength={}, conversationId='{}'",
                request != null && request.getQuery() != null ? request.getQuery().length() : 0,
                request != null ? request.getConversationId() : null);
        OrderAgentResponse response = orderAgentService.ask(request, principal.actorUserId(),
                !ImpersonationService.isImpersonating(principal));
        feedbackService.registerResponse(response, principal.userId(), principal.actorUserId(),
                !ImpersonationService.isImpersonating(principal));
        return ApiResponse.success(response);
    }

    /**
     * 异步执行订单 Agent 问答，并通过 SSE 逐段推送模型内容和最终结果。
     *
     * @param request 用户问题、会话编号和检索参数
     * @param principal 当前登录身份；演示代入身份不能确认敏感操作
     * @param servletResponse HTTP 响应对象，用于设置禁止缓存和代理缓冲的 SSE 响应头
     * @return 已注册到流会话中的 SSE 发送器
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody AskRequest request,
                                @AuthenticationPrincipal SecurityUserPrincipal principal,
                                HttpServletResponse servletResponse) {
        servletResponse.setHeader("Cache-Control", "no-cache, no-transform");
        servletResponse.setHeader("X-Accel-Buffering", "no");

        AgentStreamSessionRegistry.StreamHandle stream = streamRegistry.open();
        streamRegistry.start(stream.streamId());
        boolean sensitiveConfirmationAllowed = !ImpersonationService.isImpersonating(principal);
        try {
            streamExecutor.execute(() -> runStreamRequest(
                    request, principal.userId(), principal.actorUserId(),
                    sensitiveConfirmationAllowed, stream.streamId()));
        } catch (RuntimeException e) {
            log.warn("Unable to schedule agent stream {}: {}", stream.streamId(), e.getMessage());
            streamRegistry.fail(stream.streamId(), new AgentStreamSessionRegistry.StreamError(
                    503, "服务繁忙，请稍后重试", true));
        }
        return stream.emitter();
    }

    /**
     * 使用人工审核结果恢复被中断的 Agent Graph。
     * approved=true 时继续执行敏感操作，approved=false 时按修订问题重新规划。
     *
     * @param request Graph 线程编号、审核结论以及可选的修订问题
     * @param principal 当前登录身份；演示代入身份禁止恢复敏感操作
     * @return 恢复执行后的 Agent 回答和最新会话状态
     */
    @PostMapping("/resume")
    public ApiResponse<OrderAgentResponse> resume(@RequestBody HumanFeedbackRequest request,
                                                  @AuthenticationPrincipal SecurityUserPrincipal principal) {
        rejectImpersonatedSensitiveOperation(principal);
        log.info("POST /agent/order/resume - threadId='{}', approved={}",
                request != null ? request.getThreadId() : null,
                request != null ? request.getApproved() : null);
        OrderAgentResponse response = orderAgentService.resume(request, principal.actorUserId());
        feedbackService.registerResponse(response, principal.userId(), principal.actorUserId(), true);
        return ApiResponse.success(response);
    }

    /**
     * 放弃指定会话中等待人工确认的敏感操作，并结束对应的待恢复状态。
     *
     * @param request 待放弃会话的 Graph 线程编号
     * @param principal 当前登录身份；演示代入身份禁止放弃敏感操作
     * @return 是否成功放弃该待确认操作
     */
    @PostMapping("/abandon")
    public ApiResponse<Boolean> abandon(@RequestBody AbandonConversationRequest request,
                                        @AuthenticationPrincipal SecurityUserPrincipal principal) {
        rejectImpersonatedSensitiveOperation(principal);
        return ApiResponse.success(orderAgentService.abandon(request, principal.actorUserId()));
    }

    private static void rejectImpersonatedSensitiveOperation(SecurityUserPrincipal principal) {
        if (ImpersonationService.isImpersonating(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "演示身份不能确认或放弃敏感业务操作");
        }
    }

    private void runStreamRequest(AskRequest request, long appUserId, String actorUserId,
                                  boolean sensitiveConfirmationAllowed, String streamId) {
        try {
            OrderAgentResponse response = orderAgentService.askStreaming(
                    request, actorUserId, sensitiveConfirmationAllowed, streamId);
            feedbackService.registerResponse(
                    response, appUserId, actorUserId, sensitiveConfirmationAllowed);
            streamRegistry.complete(streamId, response);
        } catch (AgentStreamDisconnectedException e) {
            log.debug("Agent stream {} disconnected", streamId);
        } catch (RuntimeException e) {
            log.error("Agent stream {} failed", streamId, e);
            streamRegistry.fail(streamId, toStreamError(e));
        } finally {
            streamRegistry.release(streamId);
        }
    }

    private static AgentStreamSessionRegistry.StreamError toStreamError(RuntimeException error) {
        if (error instanceof ResponseStatusException statusException) {
            String message = statusException.getReason() != null
                    ? statusException.getReason() : "请求处理失败";
            return new AgentStreamSessionRegistry.StreamError(
                    statusException.getStatusCode().value(), message,
                    statusException.getStatusCode().is5xxServerError());
        }
        if (error instanceof IllegalArgumentException) {
            return new AgentStreamSessionRegistry.StreamError(400, error.getMessage(), false);
        }
        return new AgentStreamSessionRegistry.StreamError(500, "请求处理失败，请稍后重试", true);
    }
}
