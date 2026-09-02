package com.studypilot.infrastructure.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CosineVectorStoreTest {

    /** 用内存版验证余弦计算与排序（不依赖 Spring/JPA/SQLite）。 */
    static class InMemoryVectorStore implements VectorStore {
        java.util.Map<String, float[]> data = new java.util.LinkedHashMap<>();

        @Override
        public void upsert(List<String> chunkIds, List<float[]> vectors) {
            for (int i = 0; i < chunkIds.size(); i++) {
                data.put(chunkIds.get(i), CosineVectorStore.normalize(vectors.get(i)));
            }
        }

        @Override
        public List<ScoredId> search(float[] queryVector, int topK) {
            float[] q = CosineVectorStore.normalize(queryVector);
            return data.entrySet().stream()
                    .map(e -> new ScoredId(e.getKey(), CosineVectorStore.dot(q, e.getValue())))
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(topK)
                    .toList();
        }

        @Override
        public void deleteByChunkIds(List<String> chunkIds) {
            chunkIds.forEach(data::remove);
        }
    }

    @Test
    void cosineRanksSimilarVectorsFirst() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(List.of("a", "b"), List.of(
                new float[]{1f, 0f, 0f},   // 与查询完全一致
                new float[]{0f, 1f, 0f}    // 正交
        ));

        List<VectorStore.ScoredId> hits = store.search(new float[]{1f, 0f, 0f}, 2);

        assertEquals("a", hits.get(0).chunkId());
        assertEquals(1.0, hits.get(0).score(), 1e-6);
        assertTrue(hits.get(1).score() < 0.01);
    }

    @Test
    void normalizeMakesDotProductEqualCosine() {
        float[] v = new float[]{3f, 4f};
        float[] n = CosineVectorStore.normalize(v);
        assertEquals(1.0, CosineVectorStore.dot(n, n), 1e-6);
    }
}
