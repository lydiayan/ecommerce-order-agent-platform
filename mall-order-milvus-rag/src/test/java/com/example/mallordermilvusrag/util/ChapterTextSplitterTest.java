package com.example.mallordermilvusrag.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChapterTextSplitterTest {

    @Test
    void shouldNotMixAdjacentChapters() {
        String text = """
                第五章 行为规范
                第十条 严禁行为：在办公场所进行赌博、传播不当信息、骚扰他人等行为。违反者视情节给予警告、记过、降级、解除劳动合同，构成违法犯罪的依法移送司法机关。
                第十一条 信息安全：所有员工须签署《信息安全承诺书》，办公电脑必须安装公司统一部署的终端安全软件，禁止安装未经IT部门批准的P2P软件、翻墙工具。敏感文件传输须使用公司加密通道，禁止通过个人微信/QQ/网盘传输含客户数据或商业机密的文件。
                第六章 培训与发展
                第十二条 新员工入职培训包括公司文化与价值观。
                """;

        List<String> sections = ChapterTextSplitter.splitByChapter(text);

        assertTrue(sections.size() >= 2, "sections: " + sections);
        String chapter5 = sections.stream()
                .filter(s -> s.contains("第五章"))
                .findFirst()
                .orElseThrow();
        assertTrue(chapter5.contains("第十条"));
        assertTrue(chapter5.contains("第十一条"));
        assertFalse(chapter5.contains("第六章"), chapter5);

        String chapter6 = sections.stream()
                .filter(s -> s.contains("第六章"))
                .findFirst()
                .orElseThrow();
        assertTrue(chapter6.contains("第十二条"));
        assertFalse(chapter6.contains("第十一条"), chapter6);
    }
}
