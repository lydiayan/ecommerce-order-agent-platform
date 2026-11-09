package com.css.mallorderagent.playground;

import com.css.mallorderagent.security.SecurityAuditService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/admin/playground")
public class AdminPlaygroundController {

    private final AdminPlaygroundService playgroundService;
    private final SecurityAuditService auditService;

    public AdminPlaygroundController(AdminPlaygroundService playgroundService,
                                     SecurityAuditService auditService) {
        this.playgroundService = playgroundService;
        this.auditService = auditService;
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody PlaygroundAskRequest input,
                                   @AuthenticationPrincipal SecurityUserPrincipal principal,
                                   HttpServletRequest request) {
        if (input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }
        try {
            var response = playgroundService.ask(
                    principal.userId(), input.actorUserId(), input.query(),
                    input.conversationId(), input.topK(),
                    Boolean.TRUE.equals(input.webSearchEnabled()));
            String detail = "conversation=" + response.conversationId()
                    + ",strategy=" + value(response.planStrategy())
                    + ",webSearch=" + response.webSearchEnabled()
                    + ",trace=" + value(response.traceId());
            auditService.record("ADMIN_PLAYGROUND_ASK", principal.getUsername(),
                    response.actorUserId(), "SUCCESS", detail, request);
            return success(response);
        } catch (IllegalArgumentException e) {
            auditService.record("ADMIN_PLAYGROUND_ASK", principal.getUsername(),
                    input.actorUserId(), "DENIED", e.getMessage(), request);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestBody PlaygroundResetRequest input,
                                     @AuthenticationPrincipal SecurityUserPrincipal principal,
                                     HttpServletRequest request) {
        if (input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }
        boolean removed = playgroundService.reset(
                principal.userId(), input.actorUserId(), input.conversationId());
        auditService.record("ADMIN_PLAYGROUND_RESET", principal.getUsername(),
                input.actorUserId(), "SUCCESS", "sessionExisted=" + removed, request);
        return success(Map.of("removed", removed));
    }

    private static String value(String value) {
        return value != null && !value.isBlank() ? value : "none";
    }

    private static Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }

    public record PlaygroundAskRequest(String actorUserId, String query,
                                       String conversationId, Integer topK,
                                       Boolean webSearchEnabled) {
    }

    public record PlaygroundResetRequest(String actorUserId, String conversationId) {
    }
}
