package com.example.mallordermilvusrag.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagServiceFilterExpressionTest {

    @Test
    void buildsServerDerivedMultiScopeExpression() {
        String expression = RagService.buildFilterExpression(
                null, null, null, "1.0",
                List.of("Engineering"), List.of("public", "developer"));

        assertEquals("version == \"1.0\" && (role in [\"public\", \"developer\"]"
                + " || department in [\"Engineering\"])", expression);
    }
}
