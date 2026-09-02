package com.studypilot.model;

import java.util.List;

/**
 * 切分后的文本块。headingPath 保存块所属标题链（如 "Java并发 / 线程池 / 核心参数"），
 * 既用于回答时的引用溯源，也让检索上下文带上标题语义。
 */
public record TextChunk(
        String id,
        Long docId,
        String docName,
        String headingPath,   // 标题链，用 " / " 连接
        int seq,              // 文档内块序号
        String content
) {
    public String sourceLabel() {
        return docName + (headingPath == null || headingPath.isBlank() ? "" : " · " + headingPath);
    }

    public static TextChunk of(Long docId, String docName, String headingPath, int seq, String content) {
        return new TextChunk(docId + "-" + seq, docId, docName, headingPath, seq, content);
    }
}
