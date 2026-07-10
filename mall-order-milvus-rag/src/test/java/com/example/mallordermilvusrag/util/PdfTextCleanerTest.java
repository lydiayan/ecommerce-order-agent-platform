package com.example.mallordermilvusrag.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PdfTextCleanerTest {

    @Test
    void shouldRemovePdfLayoutNoiseAndInsertSectionBreaks() {
        String raw = """
                的部分按60%发放。婚假为3个工作日，产假/陪产假按国家及地方最新规定执行。
                
                       第四章          薪酬福利
                       第八条      薪酬结构       = 基本工资（60%）+
                """;

        String cleaned = PdfTextCleaner.clean(raw);

        assertFalse(cleaned.contains("          "), cleaned);
        assertTrue(cleaned.contains("婚假为3个工作日"), cleaned);
        assertTrue(cleaned.contains("第四章"), cleaned);
        assertTrue(cleaned.contains("薪酬福利"), cleaned);
        assertTrue(cleaned.indexOf("第四章") > cleaned.indexOf("婚假"), cleaned);
    }

    @Test
    void shouldReturnEmptyForBlankInput() {
        assertEquals("", PdfTextCleaner.clean(null));
        assertEquals("", PdfTextCleaner.clean("   "));
    }
}
