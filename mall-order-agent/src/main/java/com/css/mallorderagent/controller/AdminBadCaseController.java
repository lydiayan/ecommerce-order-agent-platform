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

    @GetMapping("/metrics")
    public ApiResponse<AgentFeedbackService.FeedbackMetricsView> metrics(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(feedbackService.metrics(days));
    }

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
