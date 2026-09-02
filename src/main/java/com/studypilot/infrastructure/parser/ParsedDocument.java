package com.studypilot.infrastructure.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 解析结果：文本 + 标题结构（heading 行记录标题级别，供结构感知切分使用）。
 */
public record ParsedDocument(
        String name,
        String sourceType,
        List<Heading> headings  // 含正文行；heading.isHeading=true 时 level 有效
) {
    public record Heading(int level, String text, boolean isHeading) {
    }

    public interface DocumentParser {
        boolean supports(String sourceType);

        /** 解析输入流为标题结构化文本（Markdown/TXT 用正则切标题，PDF 退化为单级标题）。 */
        ParsedDocument parse(String name, String sourceType, InputStream in) throws IOException;
    }
}
