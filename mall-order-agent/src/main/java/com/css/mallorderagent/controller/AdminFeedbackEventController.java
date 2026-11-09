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

    /**
     * 将指定的反馈死信事件重新放回待处理状态，供同步任务再次投递。
     *
     * @param id 反馈 Outbox 事件主键
     * @return 是否成功将该死信事件标记为可重放
     */
    @PostMapping("/{id}/replay")
    public ApiResponse<Boolean> replay(@PathVariable long id) {
        return ApiResponse.success(repository.replayDead(id));
    }
}
