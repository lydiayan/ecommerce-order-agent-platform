package com.css.mallorderagent.controller;

import com.css.mallorderagent.dto.AgentFeedbackRequest;
import com.css.mallorderagent.feedback.AgentFeedbackService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.example.mallordermilvusrag.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/feedback")
public class FeedbackController {

    private final AgentFeedbackService feedbackService;

    public FeedbackController(AgentFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ApiResponse<AgentFeedbackService.FeedbackView> submit(
            @RequestBody AgentFeedbackRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ApiResponse.success(feedbackService.submit(request, principal.userId()));
    }

    @GetMapping("/{responseId}")
    public ApiResponse<AgentFeedbackService.FeedbackView> find(
            @PathVariable String responseId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ApiResponse.success(feedbackService.find(responseId, principal.userId()));
    }

    @DeleteMapping("/{responseId}")
    public ApiResponse<AgentFeedbackService.FeedbackView> cancel(
            @PathVariable String responseId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ApiResponse.success(feedbackService.cancel(responseId, principal.userId()));
    }
}
