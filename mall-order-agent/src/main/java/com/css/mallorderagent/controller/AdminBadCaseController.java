package com.css.mallorderagent.controller;

import com.css.mallorderagent.dto.BadCaseUpdateRequest;
import com.css.mallorderagent.feedback.AgentFeedbackService;
import com.css.mallorderagent.security.SecurityAuditService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.example.mallordermilvusrag.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/admin/bad-cases")
public class AdminBadCaseController {

    private final AgentFeedbackService feedbackService;
    private final SecurityAuditService auditService;

    public AdminBadCaseController(AgentFeedbackService feedbackService,
                                  SecurityAuditService auditService) {
        this.feedbackService = feedbackService;
        this.auditService = auditService;
    }

    /**
     * 按条件查询反馈坏案例列表，供管理员筛选和复盘 Agent 失败样本。
     *
     * @param status 坏案例处理状态；为空时不过滤
     * @param reason 反馈原因；为空时不过滤
     * @param strategy Agent 规划策略；为空时不过滤
     * @param modelName 模型名称；为空时不过滤
     * @param agentVersion Agent 版本；为空时不过滤
     * @param from 反馈日期下界（含）；为空时不限制开始日期
     * @param to 反馈日期上界（含）；为空时不限制结束日期
     * @param limit 最大返回条数，默认 100
     * @return 符合条件的坏案例摘要列表
     */
    @GetMapping
    public ApiResponse<List<AgentFeedbackService.BadCaseListView>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String agentVersion,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(feedbackService.findBadCases(
                status, reason, strategy, modelName, agentVersion, from, to, limit));
    }

    /**
     * 汇总指定时间窗口内的反馈指标，供管理页面展示质量趋势。
     *
     * @param days 向前统计的天数，默认 30 天
     * @return 反馈数量、评分和坏案例等聚合指标
     */
    @GetMapping("/metrics")
    public ApiResponse<AgentFeedbackService.FeedbackMetricsView> metrics(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(feedbackService.metrics(days));
    }

    /**
     * 查询单个坏案例的完整详情，并记录管理员查看审计日志。
     *
     * @param badCaseId 坏案例主键
     * @param principal 当前登录的管理员身份
     * @param servletRequest 当前 HTTP 请求，用于提取安全审计信息
     * @return 指定坏案例的对话、Trace 摘要和处理状态
     */
    @GetMapping("/{badCaseId}")
    public ApiResponse<AgentFeedbackService.BadCaseDetailView> findOne(
            @PathVariable long badCaseId,
            @AuthenticationPrincipal SecurityUserPrincipal principal,
            HttpServletRequest servletRequest) {
        AgentFeedbackService.BadCaseDetailView detail = feedbackService.findBadCase(badCaseId);
        auditService.record("BAD_CASE_VIEW", principal.getUsername(), Long.toString(badCaseId),
                "SUCCESS", detail.status(), servletRequest);
        return ApiResponse.success(detail);
    }

    /**
     * 更新坏案例的处理状态、归因或备注，并记录管理员操作审计日志。
     *
     * @param badCaseId 待更新的坏案例主键
     * @param request 坏案例状态、分类、负责人、根因、处理结论和修复版本
     * @param principal 当前登录的管理员身份
     * @param servletRequest 当前 HTTP 请求，用于提取安全审计信息
     * @return 更新后的坏案例详情
     */
    @PostMapping("/{badCaseId}")
    public ApiResponse<AgentFeedbackService.BadCaseDetailView> update(
            @PathVariable long badCaseId,
            @RequestBody BadCaseUpdateRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal,
            HttpServletRequest servletRequest) {
        AgentFeedbackService.BadCaseDetailView updated = feedbackService
                .updateBadCase(badCaseId, request, principal.userId());
        auditService.record("BAD_CASE_UPDATE", principal.getUsername(), Long.toString(badCaseId),
                "SUCCESS", updated.status(), servletRequest);
        return ApiResponse.success(updated);
    }
}
