package example.mallordercmp_ssoclient.service;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
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
 * poi-tl：Word 表格支持单行占位符（test.id / test.name）与多行列表循环（tests 列表）。
 * <p>
 * 列表模式需在模板表格中：<br>
 * 第一行表头：测试id | 测试name<br>
 * 第二行（循环行）：{{id}} | {{name}} —— 与 {@link LoopRowTableRenderPolicy} 绑定的 key「tests」对应 List 中每项的字段名。
 */
@Service
public class PolTlTestService {

    private static final Logger log = LoggerFactory.getLogger(PolTlTestService.class);

    /** 与 LoopRowTableRenderPolicy 绑定名一致，模板循环行内写 {{id}}、{{name}} */
    public static final String LOOP_LIST_KEY = "tests";

    /**
     * 单行渲染（兼容旧接口）：内部转为仅含一项的列表，仍使用「单条占位」模板 {@link #buildSingleRowTemplateBytes()}。
     */
    public byte[] renderTestTable(String classpathTemplate, String testId, String testName) {
        Map<String, Object> test = new HashMap<>();
        test.put("id", testId);
        test.put("name", testName);
        Map<String, Object> data = new HashMap<>();
        data.put("test", test);
        try {
            return renderWithSinglePlaceholders(classpathTemplate, data);
        } catch (IOException e) {
            log.error("poi-tl 渲染失败", e);
            throw new IllegalStateException("生成 Word 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 表格按列表多行渲染：数据 key 为 {@value #LOOP_LIST_KEY}，每项含 id、name（与模板 {{id}}、{{name}} 对应）。
     */
    public byte[] renderTestTableList(String classpathTemplate, List<Map<String, Object>> testRows) {
        if (testRows == null || testRows.isEmpty()) {
            testRows = new ArrayList<>();
            testRows.add(Map.of("id", "", "name", ""));
        }
        try (InputStream templateIn = openTemplateForList(classpathTemplate)) {
            Map<String, Object> data = new HashMap<>();
            data.put(LOOP_LIST_KEY, testRows);

            LoopRowTableRenderPolicy loopRowPolicy = new LoopRowTableRenderPolicy();
            Configure configure = Configure.builder()
                    .bind(LOOP_LIST_KEY, loopRowPolicy)
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

    private byte[] renderWithSinglePlaceholders(String classpathTemplate, Map<String, Object> data) throws IOException {
        try (InputStream templateIn = openTemplate(classpathTemplate)) {
            Configure configure = Configure.builder().build();
            try (XWPFTemplate template = XWPFTemplate.compile(templateIn, configure)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                template.render(data).write(out);
                return out.toByteArray();
            }
        }
    }

    private InputStream openTemplate(String classpathTemplate) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathTemplate);
        if (resource.exists()) {
            log.info("使用 classpath 模板: {}", classpathTemplate);
            return resource.getInputStream();
        }
        log.info("未找到 classpath 模板 {}，使用内存生成的单行占位表格", classpathTemplate);
        return new ByteArrayInputStream(buildSingleRowTemplateBytes());
    }

    /**
     * 列表模板：若用户未提供 template.docx，使用内置「表头 + 循环行 {{id}}{{name}}」。
     */
    private InputStream openTemplateForList(String classpathTemplate) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathTemplate);
        if (resource.exists()) {
            log.info("使用 classpath 列表模板: {}", classpathTemplate);
            return resource.getInputStream();
        }
        log.info("未找到 classpath 模板 {}，使用内存生成的列表循环表格", classpathTemplate);
        return new ByteArrayInputStream(buildListLoopTemplateBytes());
    }

    /**
     * 单行模板：表头 + 一行 {{test.id}} {{test.name}}
     */
    private byte[] buildSingleRowTemplateBytes() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("测试id");
            table.getRow(0).getCell(1).setText("测试name");
            table.getRow(1).getCell(0).setText("{{test.id}}");
            table.getRow(1).getCell(1).setText("{{test.name}}");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    /**
     * 列表循环模板：第一行表头，第二行为循环行（poi-tl 默认对下标为 1 的行做循环），占位 {{id}} {{name}}。
     */
    private byte[] buildListLoopTemplateBytes() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("测试id");
            table.getRow(0).getCell(1).setText("测试name");
            table.getRow(1).getCell(0).setText("{{id}}");
            table.getRow(1).getCell(1).setText("{{name}}");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }
}
