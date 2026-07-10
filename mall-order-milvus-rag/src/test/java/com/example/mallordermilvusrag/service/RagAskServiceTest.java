package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.SearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagAskServiceTest {

    @Test
    void shouldBuildContextWithSource() {
        SearchResponse.SearchHit hit = new SearchResponse.SearchHit(
                "id-1",
                "婚假为3个工作日。",
                0.8,
                0.33,
                0.8,
                new DocumentMetadata("01_HR员工手册.pdf", "HR", "hr", "3.2", "2026-06-22")
        );

        String context = RagAskService.buildContext(List.of(hit));

        assertTrue(context.contains("[1]"));
        assertTrue(context.contains("01_HR员工手册.pdf"));
        assertTrue(context.contains("婚假为3个工作日"));
    }

    @Test
    void shouldBuildUserMessage() {
        String message = RagAskService.buildUserMessage("参考资料片段", "婚假有多少天？");
        assertTrue(message.contains("参考资料片段"));
        assertTrue(message.contains("婚假有多少天？"));
    }
}
