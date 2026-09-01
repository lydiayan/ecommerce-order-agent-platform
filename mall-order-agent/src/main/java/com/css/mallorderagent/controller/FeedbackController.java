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

    /**
     * 为当前登录用户看到的一条 Agent 回复提交或覆盖反馈。
     *
     * @param request 回复编号、评价结果、原因和补充说明
     * @param principal 当前登录用户，用于校验反馈数据归属
     * @return 保存后的反馈视图
     */
    @PostMapping
    public ApiResponse<AgentFeedbackService.FeedbackView> submit(
            @RequestBody AgentFeedbackRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ApiResponse.success(feedbackService.submit(request, principal.userId()));
    }

    /**
     * 查询当前登录用户对指定 Agent 回复提交的反馈。
     *
     * @param responseId Agent 回复的唯一编号
     * @param principal 当前登录用户，用于校验反馈数据归属
     * @return 对应回复的反馈视图
     */
    @GetMapping("/{responseId}")
    public ApiResponse<AgentFeedbackService.FeedbackView> find(
            @PathVariable String responseId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ApiResponse.success(feedbackService.find(responseId, principal.userId()));
    }

    /**
     * 撤销当前登录用户对指定 Agent 回复提交的反馈。
     *
     * @param responseId Agent 回复的唯一编号
     * @param principal 当前登录用户，用于校验反馈数据归属
     * @return 已撤销的反馈视图
     */
    @DeleteMapping("/{responseId}")
    public ApiResponse<AgentFeedbackService.FeedbackView> cancel(
            @PathVariable String responseId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ApiResponse.success(feedbackService.cancel(responseId, principal.userId()));
    }
}
