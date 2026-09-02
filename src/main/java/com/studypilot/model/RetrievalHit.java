package com.studypilot.model;

/**
 * 检索命中项：块 + 混合分数 + 各通道得分（用于调参与简历量化说明）。
 */
public record RetrievalHit(
        TextChunk chunk,
        double score,         // 混合检索最终分（RRF 归一 0~1）
        double vectorScore,   // 向量相似度（余弦，0~1）
        double keywordScore,  // BM25 归一化后 0~1，未命中为 0
        int vectorRank,
        int keywordRank
) {
}
