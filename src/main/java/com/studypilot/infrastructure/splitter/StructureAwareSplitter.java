package com.studypilot.infrastructure.splitter;

import com.studypilot.infrastructure.parser.ParsedDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构感知切分器：
 * - 按标题层级将正文归入最近标题之下，保留"标题链"（headingPath）；
 * - 单块超长时按字符窗口硬切（带重叠），避免截断语义；
 * - 无标题（PDF/纯文本）时退化为按长度切分。
 */
public class StructureAwareSplitter {

    private final int maxChunkChars;
    private final int overlapChars;

    public StructureAwareSplitter(int maxChunkChars, int overlapChars) {
        if (maxChunkChars <= 0 || overlapChars < 0 || overlapChars >= maxChunkChars) {
            throw new IllegalArgumentException("切分参数必须满足 0 <= overlap < maxChunkChars");
        }
        this.maxChunkChars = maxChunkChars;
        this.overlapChars = overlapChars;
    }

    /** 产出切片：headingPath 为标题链，text 为正文内容。 */
    public List<Section> split(ParsedDocument doc) {
        List<MutableSection> sections = new ArrayList<>();
        MutableSection current = null;
        List<HeadingItem> headingStack = new ArrayList<>();

        for (ParsedDocument.Heading h : doc.headings()) {
            if (h.isHeading()) {
                // 弹出级别 >= 当前标题的父级，保持标题链只含真正的祖先
                while (!headingStack.isEmpty()
                        && headingStack.get(headingStack.size() - 1).level >= h.level()) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(new HeadingItem(h.level(), h.text()));
                if (current != null && current.hasContent()) {
                    sections.add(current);
                }
                current = new MutableSection(path(headingStack));
            } else {
                String text = h.text();
                if (text.isBlank()) {
                    continue;
                }
                if (current == null) {
                    current = new MutableSection("");
                }
                current.append(text);
            }
        }
        if (current != null && current.hasContent()) {
            sections.add(current);
        }

        // 超长 section 按字符窗口硬切（带重叠）
        List<Section> result = new ArrayList<>();
        for (MutableSection s : sections) {
            if (s.text.length() <= maxChunkChars) {
                result.add(new Section(s.headingPath, s.text.toString()));
            } else {
                result.addAll(forceSplit(s.headingPath, s.text.toString()));
            }
        }
        return result;
    }

    private List<Section> forceSplit(String headingPath, String text) {
        List<Section> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxChunkChars);
            String piece = text.substring(start, end);
            if (!piece.isBlank()) {
                out.add(new Section(headingPath, piece));
            }
            if (end >= text.length()) {
                break;
            }
            // 滑动窗口：重叠 overlapChars 字符，同时保证能前进
            start = end - overlapChars;
            if (start >= text.length()) break;
        }
        return out;
    }

    private String path(List<HeadingItem> stack) {
        List<String> names = new ArrayList<>();
        for (HeadingItem item : stack) {
            names.add(item.text);
        }
        return String.join(" / ", names);
    }

    private record HeadingItem(int level, String text) {
    }

    /** 一个待入库切片（含所属标题链）。 */
    public record Section(String headingPath, String text) {
    }

    private static final class MutableSection {
        final String headingPath;
        final StringBuilder text = new StringBuilder();

        MutableSection(String headingPath) {
            this.headingPath = headingPath;
        }

        void append(String line) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(line);
        }

        boolean hasContent() {
            return text.length() > 0;
        }
    }
}
