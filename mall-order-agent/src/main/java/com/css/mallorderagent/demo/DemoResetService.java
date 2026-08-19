package com.css.mallorderagent.demo;

import com.css.mallorderagent.service.PendingConfirmationService;
import com.css.mallorderagent.tool.client.MallOrderClient;
import com.example.mallordermemory.memory.HybridMemoryManager;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

@Service
@Profile("demo")
public class DemoResetService {

    private final DemoPersonaService personaService;
    private final MallOrderClient mallOrderClient;
    private final HybridMemoryManager hybridMemoryManager;
    private final PendingConfirmationService pendingConfirmationService;
    private final JdbcTemplate jdbcTemplate;

    public DemoResetService(DemoPersonaService personaService,
                            MallOrderClient mallOrderClient,
                            HybridMemoryManager hybridMemoryManager,
                            PendingConfirmationService pendingConfirmationService,
                            JdbcTemplate jdbcTemplate) {
        this.personaService = personaService;
        this.mallOrderClient = mallOrderClient;
        this.hybridMemoryManager = hybridMemoryManager;
        this.pendingConfirmationService = pendingConfirmationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DemoResetResult reset() {
        List<String> actorUserIds = personaService.findAllActorUserIds();
        List<String> failures = new ArrayList<>();
        int deletedProfiles = 0;
        try {
            mallOrderClient.resetDemoOrders();
        } catch (RuntimeException e) {
            failures.add("订单数据：" + e.getMessage());
        }
        try {
            hybridMemoryManager.clearUsers(actorUserIds);
        } catch (RuntimeException e) {
            failures.add("会话记忆：" + e.getMessage());
        }
        try {
            deletedProfiles = deleteProfiles(actorUserIds);
        } catch (RuntimeException e) {
            failures.add("动态画像：" + e.getMessage());
        }
        try {
            pendingConfirmationService.clearAll();
        } catch (RuntimeException e) {
            failures.add("待确认状态：" + e.getMessage());
        }
        boolean success = failures.isEmpty();
        return new DemoResetResult(success, actorUserIds.size(), deletedProfiles,
                success ? "演示订单、售后单、会话记忆和动态画像已恢复" : "部分重置步骤失败",
                List.copyOf(failures));
    }

    private int deleteProfiles(List<String> userIds) {
        if (userIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(userIds.size(), "?"));
        return jdbcTemplate.update("DELETE FROM user_profile WHERE user_id IN (" + placeholders + ")",
                userIds.toArray());
    }

    public record DemoResetResult(boolean success, int personaCount, int deletedProfiles,
                                  String message, List<String> failures) {
    }
}
