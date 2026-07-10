package example.mallordercmp_ssoclient.controller;

import example.mallordercmp_ssoclient.service.PolTlTestService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 poi-tl 将数据写入 Word 表格（模板路径：classpath:jyy/pol-tl测试/template.docx，
 * 若不存在则运行时生成含占位符 {{test.id}}、{{test.name}} 的表格）。
 */
@RestController
@RequestMapping("/api/pol-tl")
public class PolTlTestController {

    private static final String TEMPLATE_PATH = "jyy/pol-tl测试/template.docx";

    private final PolTlTestService polTlTestService;

    public PolTlTestController(PolTlTestService polTlTestService) {
        this.polTlTestService = polTlTestService;
    }

    /**
     * 生成 Word：表格列「测试id」「测试name」，数据行填充 {{test.id}}、{{test.name}}
     *
     * @param testId   测试 id
     * @param testName 测试 name
     */
    @GetMapping(value = "/test/export", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> exportWord(
            @RequestParam(defaultValue = "T001") String testId,
            @RequestParam(defaultValue = "示例名称") String testName) {
        byte[] docx = polTlTestService.renderTestTable(TEMPLATE_PATH, testId, testName);
        String filename = "pol-tl-test-" + testId + ".docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    /**
     * JSON 提交：<br>
     * - 单行：<code>{"test": {"id": "1", "name": "名称"}}</code><br>
     * - 多行列表：<code>{"tests": [{"id":"1","name":"a"},{"id":"2","name":"b"}]}</code>（表格循环，与模板 {{id}}/{{name}} 对应）
     */
    @PostMapping("/test/export")
    public ResponseEntity<byte[]> exportWordPost(@RequestBody Map<String, Object> body) {
        if (body.containsKey("tests")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("tests");
            List<Map<String, Object>> normalized = new ArrayList<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", String.valueOf(row.getOrDefault("id", "")));
                    m.put("name", String.valueOf(row.getOrDefault("name", "")));
                    normalized.add(m);
                }
            }
            byte[] docx = polTlTestService.renderTestTableList(TEMPLATE_PATH, normalized);
            String filename = "pol-tl-test-list.docx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docx);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> test = (Map<String, Object>) body.getOrDefault("test", Map.of());
        String id = String.valueOf(test.getOrDefault("id", "T001"));
        String name = String.valueOf(test.getOrDefault("name", "示例名称"));
        byte[] docx = polTlTestService.renderTestTable(TEMPLATE_PATH, id, name);
        String filename = "pol-tl-test-" + id + ".docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }
}
