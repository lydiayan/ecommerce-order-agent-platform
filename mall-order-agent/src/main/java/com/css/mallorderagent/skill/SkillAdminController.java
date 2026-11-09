package com.css.mallorderagent.skill;

import com.css.mallorderagent.security.SecurityAuditService;
import com.css.mallorderagent.security.SecurityUserPrincipal;
import com.css.mallorderagent.skill.model.SkillSourceRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/admin/skills")
public class SkillAdminController {

    private final SkillManagementService skillService;
    private final SecurityAuditService auditService;

    public SkillAdminController(SkillManagementService skillService, SecurityAuditService auditService) {
        this.skillService = skillService;
        this.auditService = auditService;
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody SkillSourceRequest input,
                                       @AuthenticationPrincipal SecurityUserPrincipal principal,
                                       HttpServletRequest request) {
        return runValidated(() -> {
            var preview = skillService.preview(input);
            auditService.record("SKILL_PREVIEW", principal.getUsername(), preview.name(),
                    "SUCCESS", preview.packageDigest(), request);
            return success(preview);
        });
    }

    @PostMapping("/install")
    public Map<String, Object> install(@RequestBody SkillSourceRequest input,
                                       @AuthenticationPrincipal SecurityUserPrincipal principal,
                                       HttpServletRequest request) {
        return runValidated(() -> {
            var version = skillService.install(input, principal.getUsername());
            auditService.record("SKILL_INSTALL", principal.getUsername(), Long.toString(version.skillId()),
                    "SUCCESS", version.packageDigest(), request);
            return success(version);
        });
    }

    @GetMapping
    public Map<String, Object> list() {
        return success(skillService.list());
    }

    @GetMapping("/{skillId}/versions")
    public Map<String, Object> versions(@PathVariable long skillId) {
        return success(skillService.versions(skillId));
    }

    @PostMapping("/{skillId}/versions/{versionId}/activate")
    public Map<String, Object> activate(@PathVariable long skillId,
                                        @PathVariable long versionId,
                                        @AuthenticationPrincipal SecurityUserPrincipal principal,
                                        HttpServletRequest request) {
        skillService.activate(skillId, versionId);
        auditService.record("SKILL_ACTIVATE", principal.getUsername(), Long.toString(skillId),
                "SUCCESS", "version=" + versionId, request);
        return success(Map.of());
    }

    @PostMapping("/{skillId}/activate")
    public Map<String, Object> activate(@PathVariable long skillId,
                                        @RequestBody SkillVersionRequest input,
                                        @AuthenticationPrincipal SecurityUserPrincipal principal,
                                        HttpServletRequest request) {
        return activate(skillId, requireVersion(input), principal, request);
    }

    @PostMapping("/{skillId}/rollback")
    public Map<String, Object> rollback(@PathVariable long skillId,
                                        @RequestBody SkillVersionRequest input,
                                        @AuthenticationPrincipal SecurityUserPrincipal principal,
                                        HttpServletRequest request) {
        long versionId = requireVersion(input);
        skillService.activate(skillId, versionId);
        auditService.record("SKILL_ROLLBACK", principal.getUsername(), Long.toString(skillId),
                "SUCCESS", "version=" + versionId, request);
        return success(Map.of());
    }

    @PostMapping("/{skillId}/disable")
    public Map<String, Object> disable(@PathVariable long skillId,
                                       @AuthenticationPrincipal SecurityUserPrincipal principal,
                                       HttpServletRequest request) {
        skillService.disable(skillId);
        auditService.record("SKILL_DISABLE", principal.getUsername(), Long.toString(skillId),
                "SUCCESS", null, request);
        return success(Map.of());
    }

    @PostMapping("/{skillId}/policy")
    public Map<String, Object> updatePolicy(@PathVariable long skillId,
                                            @RequestBody SkillPolicyRequest input,
                                            @AuthenticationPrincipal SecurityUserPrincipal principal,
                                            HttpServletRequest request) {
        if (input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "policy request is required");
        }
        skillService.updatePolicy(skillId, input.allowImplicitInvocation());
        auditService.record("SKILL_POLICY_UPDATE", principal.getUsername(), Long.toString(skillId),
                "SUCCESS", "implicit=" + input.allowImplicitInvocation(), request);
        return success(Map.of());
    }

    private static Map<String, Object> runValidated(Supplier<Map<String, Object>> action) {
        try {
            return action.get();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private static long requireVersion(SkillVersionRequest input) {
        if (input == null || input.versionId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionId must be positive");
        }
        return input.versionId();
    }

    private static Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data != null ? data : Map.of());
    }

    public record SkillPolicyRequest(boolean allowImplicitInvocation) {
    }

    public record SkillVersionRequest(long versionId) {
    }
}
