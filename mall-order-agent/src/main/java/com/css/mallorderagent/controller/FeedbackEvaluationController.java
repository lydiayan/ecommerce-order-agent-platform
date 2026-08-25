package com.css.mallorderagent.controller;

import com.css.mallorderagent.feedback.AgentFeedbackService;
import com.example.mallordermilvusrag.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Scoped, sanitized snapshot endpoint used by AgentInsight's evaluator. */
@RestController
@RequestMapping("/internal/feedback")
public class FeedbackEvaluationController {

    private final AgentFeedbackService feedbackService;

    public FeedbackEvaluationController(AgentFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/responses/{responseId}")
    public ApiResponse<AgentFeedbackService.EvaluationSnapshotView> snapshot(
            @PathVariable String responseId) {
        return ApiResponse.success(feedbackService.evaluationSnapshot(responseId));
    }
}
