package com.css.mallorderagent.controller;

import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.AbandonConversationRequest;
import com.css.mallorderagent.dto.HumanFeedbackRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.css.mallorderagent.service.OrderAgentService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.css.mallorderagent.security.ImpersonationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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

    public OrderAgentController(OrderAgentService orderAgentService) {
        this.orderAgentService = orderAgentService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Order agent is running");
    }

    /**
     * 订单 Agent 问答：Memory + RAG + Planner + LLM。
     */
    @PostMapping("/ask")
    public ApiResponse<OrderAgentResponse> ask(@RequestBody AskRequest request,
                                               @AuthenticationPrincipal SecurityUserPrincipal principal) {
        log.info("POST /agent/order/ask - queryLength={}, conversationId='{}'",
                request != null && request.getQuery() != null ? request.getQuery().length() : 0,
                request != null ? request.getConversationId() : null);
        OrderAgentResponse response = orderAgentService.ask(request, principal.actorUserId(),
                !ImpersonationService.isImpersonating(principal));
        return ApiResponse.success(response);
    }

    /**
     * 人工审核后恢复 Graph：approved=true 继续到 answer；approved=false 带 revisedQuery 回到 prompt 重写。
     */
    @PostMapping("/resume")
    public ApiResponse<OrderAgentResponse> resume(@RequestBody HumanFeedbackRequest request,
                                                  @AuthenticationPrincipal SecurityUserPrincipal principal) {
        rejectImpersonatedSensitiveOperation(principal);
        log.info("POST /agent/order/resume - threadId='{}', approved={}",
                request != null ? request.getThreadId() : null,
                request != null ? request.getApproved() : null);
        OrderAgentResponse response = orderAgentService.resume(request, principal.actorUserId());
        return ApiResponse.success(response);
    }

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
}
