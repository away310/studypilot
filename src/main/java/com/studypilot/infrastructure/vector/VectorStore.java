package com.studypilot.infrastructure.vector;

import java.util.List;

/**
 * 向量存储抽象。当前默认实现为自研 SQLite 余弦扫描（无 Docker 依赖）；
 * 若知识库规模增长到需要 ANN，可替换为 Qdrant/FAISS 实现而不影响上层。
 */
public interface VectorStore {

    /** 批量写入（chunkId 与向量一一对应，调用方负责归一化）。 */
    void upsert(List<String> chunkIds, List<float[]> vectors);

    /** 余弦检索 top-k，返回 (chunkId, 相似度)。 */
    List<ScoredId> search(float[] queryVector, int topK);

    void deleteByChunkIds(List<String> chunkIds);

    record ScoredId(String chunkId, double score) {
    }
}
