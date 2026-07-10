package com.css.test;

import com.css.test.vo.Goods;
import com.css.test.vo.PaymentHackData;
import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 列表写入 Word 表格：使用 poi-tl {@link com.css.test.PolTlTestService#renderTestTableList}，
 * 请求体中 {@code tests} 为数组，每项 {@code id}、{@code name}。
 */
@RestController
@RequestMapping("/api/pol-tl")
public class PolTlTestController {

    private static final String TEMPLATE_PATH = "pol-tl测试.docx";

    private final PolTlTestService polTlTestService;

    public PolTlTestController(PolTlTestService polTlTestService) {
        this.polTlTestService = polTlTestService;
    }

    /**
     * GET：演示多条（两个固定示例行），验证列表循环。
     */
    @GetMapping(value = "/test/export", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> exportWord() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", "T001");
        r1.put("name", "示例一");
        rows.add(r1);
        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", "T002");
        r2.put("name", "示例二");
        rows.add(r2);
        byte[] docx = polTlTestService.renderTestTableList(TEMPLATE_PATH, rows);
        return docxResponse(docx, "pol-tl-test-list.docx");
    }

    /**
     * POST：多行列表（推荐）
     * <pre>
     * {"tests":[{"id":"1","name":"a"},{"id":"2","name":"b"}]}
     * </pre>
     */
    @PostMapping("/test/export")
    public ResponseEntity<byte[]> exportWordPost(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tests = (List<Map<String, Object>>) body.get("tests");
        if (tests == null || tests.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] docx = polTlTestService.renderTestTableList(TEMPLATE_PATH, tests);
        return docxResponse(docx, "pol-tl-test-list.docx");
    }

    /**
     * POST：仅一行（与多条同一套 Loop 逻辑，仅 List 长度为 1）
     */
    @PostMapping("/test/export/one")
    public ResponseEntity<byte[]> exportOne(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> test = (Map<String, Object>) body.getOrDefault("test", Map.of());
        String id = String.valueOf(test.getOrDefault("id", "T001"));
        String name = String.valueOf(test.getOrDefault("name", "示例名称"));
        byte[] docx = polTlTestService.renderTestTable(TEMPLATE_PATH, id, name);
        return docxResponse(docx, "pol-tl-test-" + id + ".docx");
    }

    private static ResponseEntity<byte[]> docxResponse(byte[] docx, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    @GetMapping(value = "/test/generate", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> generate() {

        byte[] docx = polTlTestService.generateDefaultDocxWithTestPlaceholders();
        return docxResponse(docx, "pol-tl-test-list.docx");
    }

    @GetMapping(value = "/test/test1")
    public void test1() throws IOException {

        PaymentHackData data = new PaymentHackData();

        List<Goods> goods = new ArrayList<>();
        Goods good = new Goods();
        good.setCount(4);
        good.setName("墙纸");
        good.setDesc("书房卧室");
        good.setDiscount(1500);
        good.setPrice(400);
        good.setTax(new Random().nextInt(10) + 20);
        good.setTotalPrice(1600);
        goods.add(good);
        goods.add(good);
        goods.add(good);
        data.setGoods(goods);



        data.setTotal("1024");

        data.setGoods2(goods);
        LoopRowTableRenderPolicy hackLoopTableRenderPolicy = new LoopRowTableRenderPolicy();
        Configure config = Configure.builder().bind("goods", hackLoopTableRenderPolicy)
                .build();
        String resource = "/Users/jyy/IdeaProjects/EcommSpringBot/Test/src/main/resources/render_hackloop.docx";
        XWPFTemplate template = XWPFTemplate.compile(resource, config).render(data);
        template.writeToFile("/Users/jyy/IdeaProjects/EcommSpringBot/Test/src/main/resources/render_hackloop.docx");

    }
    private static String normalizeClasspath(String classpathTemplate) {
        if (classpathTemplate == null || classpathTemplate.isEmpty()) {
            return "pol-tl测试.docx";
        }
        return classpathTemplate.startsWith("/") ? classpathTemplate.substring(1) : classpathTemplate;
    }

}
