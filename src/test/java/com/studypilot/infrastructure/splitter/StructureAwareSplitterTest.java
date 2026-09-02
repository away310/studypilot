package com.studypilot.infrastructure.splitter;

import com.studypilot.infrastructure.parser.MarkdownTxtParser;
import com.studypilot.infrastructure.parser.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureAwareSplitterTest {

    private final MarkdownTxtParser parser = new MarkdownTxtParser();
    private final StructureAwareSplitter splitter = new StructureAwareSplitter(800, 100);

    private ParsedDocument parseMd(String md) throws Exception {
        return parser.parse("test.md", "markdown",
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void keepsHeadingPathForEachSection() throws Exception {
        String md = """
                # RAG 是什么

                RAG 是检索增强生成。

                ## 切分

                切分质量影响检索质量。

                ## 检索

                向量检索加 BM25。
                """;
        List<StructureAwareSplitter.Section> sections = splitter.split(parseMd(md));

        assertEquals(3, sections.size());
        assertEquals("RAG 是什么 / 切分", sections.get(1).headingPath());
        assertEquals("RAG 是什么 / 检索", sections.get(2).headingPath());
        assertTrue(sections.get(1).text().contains("切分质量"));
    }

    @Test
    void forceSplitsOverlongSection() throws Exception {
        StructureAwareSplitter tiny = new StructureAwareSplitter(50, 10);
        String text = "数据".repeat(200); // 400 字符
        String md = "# 长文\n\n" + text;
        List<StructureAwareSplitter.Section> sections = tiny.split(parseMd(md));

        assertTrue(sections.size() >= 2, "超长内容应被切成多块");
        for (StructureAwareSplitter.Section s : sections) {
            assertTrue(s.text().length() <= 50, "每块不超过上限");
        }
        // 拼接（考虑重叠）后应覆盖全部内容
        int totalChars = sections.stream().mapToInt(s -> s.text().length()).sum();
        assertTrue(totalChars >= 400, "内容不应丢失");
    }

    @Test
    void parserSupportsMarkdownAndTxt() {
        assertTrue(parser.supports("markdown"));
        assertTrue(parser.supports("txt"));
    }
}
