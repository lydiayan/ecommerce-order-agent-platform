package com.css.mallorderagent.stream;

import com.css.mallorderagent.config.OrderAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStreamSessionRegistryTest {

    @Test
    void exposesExactlyOneAutowiredProductionConstructor() {
        long autowiredConstructors = Arrays.stream(AgentStreamSessionRegistry.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertEquals(1, autowiredConstructors);
    }

    @Test
    void createsIndependentRequestSessionsAndReleasesThem() {
        OrderAgentProperties properties = new OrderAgentProperties();
        AgentStreamSessionRegistry registry = new AgentStreamSessionRegistry(properties);

        var first = registry.open();
        var second = registry.open();

        assertNotEquals(first.streamId(), second.streamId());
        assertEquals(2, registry.activeStreamCount());
        registry.cancel(first.streamId());
        assertTrue(registry.isCancelled(first.streamId()));
        registry.release(first.streamId());
        registry.release(second.streamId());
        assertEquals(0, registry.activeStreamCount());
    }

    @Test
    void rejectsRequestsBeyondTheConfiguredActiveLimit() {
        OrderAgentProperties properties = new OrderAgentProperties();
        properties.getStreaming().setMaxActiveStreams(1);
        AgentStreamSessionRegistry registry = new AgentStreamSessionRegistry(properties);

        var first = registry.open();
        assertThrows(ResponseStatusException.class, registry::open);
        assertEquals(1, registry.activeStreamCount());

        registry.release(first.streamId());
        assertEquals(0, registry.activeStreamCount());
    }
}
