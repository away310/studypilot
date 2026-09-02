package com.studypilot.infrastructure.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** 按文件类型路由到具体解析器。 */
@Component
public class ParserRouter {

    private final List<ParsedDocument.DocumentParser> parsers;

    public ParserRouter(List<ParsedDocument.DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public ParsedDocument parse(String name, String sourceType, InputStream in) throws IOException {
        ParsedDocument.DocumentParser parser = parsers.stream()
                .filter(p -> p.supports(sourceType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文档类型: " + sourceType));
        return parser.parse(name, sourceType, in);
    }

    public static String normalizeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "markdown";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".pdf")) return "pdf";
        throw new IllegalArgumentException("仅支持 markdown/txt/pdf: " + fileName);
    }
}
