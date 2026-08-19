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
