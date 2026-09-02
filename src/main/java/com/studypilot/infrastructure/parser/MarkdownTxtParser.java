package com.studypilot.infrastructure.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Markdown / 纯文本解析器：
 * - Markdown：识别 # ~ ###### 标题与代码块（代码块内不识别标题）
 * - TXT：识别常见章节标题行（"第X章"、"X、"、"1. 标题"等）为一级标题
 */
@Component
public class MarkdownTxtParser implements ParsedDocument.DocumentParser {

    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*$");
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(```|~~~)");
    private static final Pattern TXT_HEADING = Pattern.compile(
            "^(第[一二三四五六七八九十百0-9]+[章节部分篇]|[0-9]+[.、．]\\s*\\S|\\d{1,2}\\.\\d{1,2}\\s+\\S{4,})");

    @Override
    public boolean supports(String sourceType) {
        return "markdown".equalsIgnoreCase(sourceType) || "txt".equalsIgnoreCase(sourceType);
    }

    @Override
    public ParsedDocument parse(String name, String sourceType, InputStream in) throws IOException {
        List<String> lines = readLines(in);
        List<ParsedDocument.Heading> headings = new ArrayList<>(lines.size());

        boolean inCode = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                headings.add(new ParsedDocument.Heading(0, "", false));
                continue;
            }
            if ("markdown".equalsIgnoreCase(sourceType)) {
                Matcher fence = CODE_FENCE.matcher(raw);
                if (fence.find()) {
                    inCode = !inCode;
                    headings.add(new ParsedDocument.Heading(0, raw.strip(), false));
                    continue;
                }
                Matcher m = MD_HEADING.matcher(raw.strip());
                if (!inCode && m.find()) {
                    headings.add(new ParsedDocument.Heading(m.group(1).length(), m.group(2).strip(), true));
                    continue;
                }
            } else {
                Matcher tm = TXT_HEADING.matcher(line);
                if (!inCode && tm.find()) {
                    headings.add(new ParsedDocument.Heading(1, line, true));
                    continue;
                }
            }
            headings.add(new ParsedDocument.Heading(0, raw.strip(), false));
        }
        return new ParsedDocument(name, sourceType.toLowerCase(), headings);
    }

    private List<String> readLines(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
