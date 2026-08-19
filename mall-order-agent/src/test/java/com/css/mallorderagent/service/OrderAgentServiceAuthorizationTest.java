package com.css.mallorderagent.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.demo.DemoPersonaService;
import com.css.mallorderagent.dto.AbandonConversationRequest;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.dto.HumanFeedbackRequest;
import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallorderobservability.trace.RagTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class OrderAgentServiceAuthorizationTest {

    @Mock private CompiledGraph graph;
    @Mock private HybridMemoryManager memory;
    @Mock private OrderAgentProperties properties;
    @Mock private RagTraceService traceService;
    @Mock private DemoPersonaService identities;

    @Test
    void clearsClientControlledIdentityAndKnowledgeScopes() {
        AskRequest request = new AskRequest();
        request.setUserId("victim");
        request.setActorUserId("admin");
        request.setRoleFilter("admin");
        request.setDepartmentFilter("secret");
        request.setSourceFilter("private.pdf");
        request.setVersionFilter("draft");
        request.setTopK(999);

        OrderAgentService.sanitizeUntrustedRequest(request);

        assertNull(request.getUserId());
        assertNull(request.getActorUserId());
        assertNull(request.getRoleFilter());
        assertNull(request.getDepartmentFilter());
        assertNull(request.getSourceFilter());
        assertNull(request.getVersionFilter());
        assertEquals(10, request.getTopK());
    }

    @Test
    void rejectsResumeAndAbandonForDifferentAuthenticatedActor() {
        PendingConfirmationService pending = new PendingConfirmationService();
        pending.markAwaiting("USER1001::conversation", "USER1001", "trace-1");
        OrderAgentService service = new OrderAgentService(
                graph, memory, properties, traceService, pending, identities);
        HumanFeedbackRequest resume = new HumanFeedbackRequest();
        resume.setThreadId("USER1001::conversation");
        resume.setApproved(true);
        AbandonConversationRequest abandon = new AbandonConversationRequest();
        abandon.setThreadId("USER1001::conversation");

        assertThrows(ResponseStatusException.class, () -> service.resume(resume, "USER1002"));
        assertThrows(ResponseStatusException.class, () -> service.abandon(abandon, "USER1002"));
    }
}
