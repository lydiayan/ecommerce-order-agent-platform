package com.css.test;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.data.RowRenderData;
import com.deepoove.poi.data.Rows;
import com.deepoove.poi.data.Tables;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表格多行循环：poi-tl 官方 {@link LoopRowTableRenderPolicy} 要求<strong>循环行</strong>内使用<strong>扁平</strong>占位符
 * {@code {{id}}}、{@code {{name}}}，与 List 每项 Map 的 key 一致；<strong>不要</strong>在循环行写 {@code {{test.id}}}（嵌套在循环里常解析不到）。
 * <p>
 * 表头仍可写「测试 id」「测试 name」，仅表示列含义；数据列对应 {@code id}、{@code name}。
 */
@Service
public class PolTlTestService {

    private static final Logger log = LoggerFactory.getLogger(PolTlTestService.class);

    /** 与 Configure#bind、data.put("tests", list) 一致 */
    public static final String LOOP_LIST_KEY = "test";

    /**
     * 生成默认空白模板 docx（未渲染数据）。<br>
     * 第二行循环占位符为 <strong>{{id}}</strong>、<strong>{{name}}</strong>（与 LoopRowTableRenderPolicy 匹配）。
     */
    public byte[] generateDefaultDocxWithTestPlaceholders() {
        try {
            return buildDefaultTemplateDocxBytes();
        } catch (IOException e) {
            throw new IllegalStateException("生成默认模板 docx 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 多行列表渲染。
     *
     * @param rows 每项含平铺的 {@code id}、{@code name}；也兼容 {@code test: {id,name}}，会展开为平铺
     */
    public byte[] renderTestTableList(String classpathTemplate, List<Map<String, Object>> rows) {
        List<Map<String, Object>> dataRows = normalizeRowsFlat(rows);
        try (InputStream templateIn = openTemplateForList(classpathTemplate)) {
            Map<String, Object> data = new HashMap<>();
            data.put("test", dataRows);

            LoopRowTableRenderPolicy loopPolicy = new LoopRowTableRenderPolicy();
            Configure configure = Configure.builder()
                    .useSpringEL(true)
                    .bind("test", loopPolicy)
                    .build();

            try (XWPFTemplate template = XWPFTemplate.compile(templateIn, configure)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                template.render(data).write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            log.error("poi-tl 列表渲染失败", e);
            throw new IllegalStateException("生成 Word 失败: " + e.getMessage(), e);
        }
    }

    public byte[] renderTestTable(String classpathTemplate, String testId, String testName) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(flatRow(testId, testName));
        return renderTestTableList(classpathTemplate, rows);
    }

    /** 循环行一行数据：仅含 id、name（扁平） */
    private static Map<String, Object> flatRow(String id, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> normalizeRowsFlat(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            List<Map<String, Object>> fallback = new ArrayList<>();
            fallback.add(flatRow("", ""));
            return fallback;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> src : rows) {
            if (src == null) {
                out.add(flatRow("", ""));
                continue;
            }
            Object nested = src.get("test");
            if (nested instanceof Map) {
                Map<String, Object> t = (Map<String, Object>) nested;
                out.add(flatRow(
                        String.valueOf(t.getOrDefault("id", "")),
                        String.valueOf(t.getOrDefault("name", ""))));
                continue;
            }
            out.add(flatRow(
                    String.valueOf(src.getOrDefault("id", "")),
                    String.valueOf(src.getOrDefault("name", ""))));
        }
        return out;
    }

    private InputStream openTemplateForList(String classpathTemplate) throws IOException {
        String path = normalizeClasspath(classpathTemplate);
        ClassPathResource resource = new ClassPathResource(path);
        if (resource.exists()) {
            log.info("使用 classpath 模板: {}", path);
            log.warn("若写入仍为空，请确认该 docx 表格<strong>第二行</strong>为 {{id}}、{{name}}（非 {{test.id}}），或删除该文件改用内置模板");
            return resource.getInputStream();
        }
        log.info("未找到 classpath 模板 {}，使用内存生成（第二行 {{id}} {{name}}）", path);
        return new ByteArrayInputStream(buildDefaultTemplateDocxBytes());
    }

    private static String normalizeClasspath(String classpathTemplate) {
        if (classpathTemplate == null || classpathTemplate.isEmpty()) {
            return "pol-tl测试.docx";
        }
        return classpathTemplate.startsWith("/") ? classpathTemplate.substring(1) : classpathTemplate;
    }

    /**
     * 默认模板：表头「测试 id」「测试 name」；第二行循环行 {{id}}、{{name}}（poi-tl 表格行循环标准写法）。
     */
    private byte[] buildDefaultTemplateDocxBytes() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 2);
            setCellPlainText(table.getRow(0).getCell(0), "测试 id");
            setCellPlainText(table.getRow(0).getCell(1), "测试 name");
            setCellPlaceholder(table.getRow(1).getCell(0), "{{id}}");
            setCellPlaceholder(table.getRow(1).getCell(1), "{{name}}");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private static void setCellPlainText(XWPFTableCell cell, String text) {
        clearCellParagraphs(cell);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
    }

    private static void setCellPlaceholder(XWPFTableCell cell, String placeholder) {
        clearCellParagraphs(cell);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(placeholder);
    }

    private static void clearCellParagraphs(XWPFTableCell cell) {
        for (int i = cell.getParagraphs().size() - 1; i >= 0; i--) {
            cell.removeParagraph(i);
        }
    }

    public byte[] test1() {
        RowRenderData row0 = Rows.of("姓名", "学历").textColor("FFFFFF")
                .bgColor("4472C4").center().create();
        RowRenderData row1 = Rows.create("李四", "博士");
        //put("table1", Tables.create(row0, row1));
        return null;
    }
}
