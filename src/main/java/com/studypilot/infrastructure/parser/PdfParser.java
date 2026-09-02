package com.studypilot.infrastructure.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析：PDFBox 抽取全文，退化为"无标题结构"（单一大块由切分器按长度切）。
 * 快版不追求版面级结构还原，README 中说明局限。
 */
@Component
public class PdfParser implements ParsedDocument.DocumentParser {

    @Override
    public boolean supports(String sourceType) {
        return "pdf".equalsIgnoreCase(sourceType);
    }

    @Override
    public ParsedDocument parse(String name, String sourceType, InputStream in) throws IOException {
        List<ParsedDocument.Heading> headings = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            for (String line : text.split("\\r?\\n")) {
                String t = line.strip();
                if (!t.isEmpty()) {
                    headings.add(new ParsedDocument.Heading(0, t, false));
                }
            }
        }
        return new ParsedDocument(name, "pdf", headings);
    }
}
