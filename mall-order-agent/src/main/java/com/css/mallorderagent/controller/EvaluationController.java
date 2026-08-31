package com.css.mallorderagent.controller;

import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.OrderAgentResponse;
import com.css.mallorderagent.service.OrderAgentService;
import com.example.mallordermilvusrag.dto.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/evaluation")
public class EvaluationController {

    private final OrderAgentService orderAgentService;
    private final DemoPersonaService identityService;

    public EvaluationController(OrderAgentService orderAgentService, DemoPersonaService identityService) {
        this.orderAgentService = orderAgentService;
        this.identityService = identityService;
    }

    /**
     * 以指定演示身份执行一次非人工审核的 Agent 问答，供自动化评测系统调用。
     *
     * @param input 评测问题、扮演身份、会话编号和召回数量；topK 为空时使用 5
     * @return 本次 Agent 回答及其 Trace、规划和工具执行摘要
     */
    @PostMapping("/ask")
    public ApiResponse<OrderAgentResponse> ask(@RequestBody EvaluationAskRequest input) {
        identityService.requirePersona(input.actorUserId());
        AskRequest request = new AskRequest();
        request.setQuery(input.query());
        request.setConversationId(input.conversationId());
        request.setTopK(input.topK() != null ? input.topK() : 5);
        return ApiResponse.success(orderAgentService.ask(request, input.actorUserId(), false));
    }

    public record EvaluationAskRequest(String query, String actorUserId,
                                       String conversationId, Integer topK) { }
}
