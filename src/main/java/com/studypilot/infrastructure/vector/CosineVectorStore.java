package com.studypilot.infrastructure.vector;

import com.studypilot.infrastructure.persistence.ChunkVectorEntity;
import com.studypilot.infrastructure.persistence.ChunkVectorRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 自研轻量向量库：向量归一化后落 SQLite，检索时全量余弦扫描。
 *
 * 为什么自研而不是直接上 Qdrant：
 * - 本机无 Docker，知识库块数在数千级别时内存余弦检索毫秒级返回，足够；
 * - 展示"向量存储抽象 + 可插拔实现"的工程能力，README 中说明何时切换 ANN。
 */
@Component
public class CosineVectorStore implements VectorStore {

    private final ChunkVectorRepository repository;

    public CosineVectorStore(ChunkVectorRepository repository) {
        this.repository = repository;
    }

    @Override
    public void upsert(List<String> chunkIds, List<float[]> vectors) {
        for (int i = 0; i < chunkIds.size(); i++) {
            repository.save(new ChunkVectorEntity(chunkIds.get(i), normalize(vectors.get(i))));
        }
    }

    @Override
    public List<ScoredId> search(float[] queryVector, int topK) {
        float[] query = normalize(queryVector);
        List<ScoredId> results = new ArrayList<>();
        for (ChunkVectorEntity row : repository.findAllForScan()) {
            double sim = dot(query, row.toFloats());
            if (!Double.isNaN(sim)) {
                results.add(new ScoredId(row.getChunkId(), sim));
            }
        }
        results.sort(Comparator.comparingDouble(ScoredId::score).reversed());
        return results.size() <= topK ? results : results.subList(0, topK);
    }

    @Override
    public void deleteByChunkIds(List<String> chunkIds) {
        if (chunkIds != null && !chunkIds.isEmpty()) {
            repository.deleteByChunkIdIn(chunkIds);
        }
    }

    static float[] normalize(float[] v) {
        double sum = 0;
        for (float f : v) sum += (double) f * f;
        double norm = Math.sqrt(sum);
        if (norm < 1e-12) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    static double dot(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double sum = 0;
        for (int i = 0; i < n; i++) sum += (double) a[i] * b[i];
        return sum; // 两向量均已归一化，点积即余弦
    }
}
