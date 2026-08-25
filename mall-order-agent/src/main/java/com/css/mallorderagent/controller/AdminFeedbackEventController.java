package com.css.mallorderagent.controller;

import com.css.mallorderagent.feedback.FeedbackEventOutboxRepository;
import com.example.mallordermilvusrag.dto.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/feedback-events")
public class AdminFeedbackEventController {

    private final FeedbackEventOutboxRepository repository;

    public AdminFeedbackEventController(FeedbackEventOutboxRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/{id}/replay")
    public ApiResponse<Boolean> replay(@PathVariable long id) {
        return ApiResponse.success(repository.replayDead(id));
    }
}
