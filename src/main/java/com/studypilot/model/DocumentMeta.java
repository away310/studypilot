package com.studypilot.model;

/**
 * 入库后的文档元数据。
 */
public record DocumentMeta(
        Long id,
        String name,          // 原始文件名
        String sourceType,    // markdown / txt / pdf
        int chunkCount,
        long createdAt
) {
}
