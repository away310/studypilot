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
 * - 每次读取并解码全部向量，适合个人小库；实际延迟需按数据规模测量；
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
        if (chunkIds.size() != vectors.size()) throw new IllegalArgumentException("向量数量不匹配");
        for (int i = 0; i < chunkIds.size(); i++) {
            repository.save(new ChunkVectorEntity(chunkIds.get(i), normalize(vectors.get(i))));
        }
    }

    @Override
    public List<ScoredId> search(float[] queryVector, int topK) {
        float[] query = normalize(queryVector);
        if (topK <= 0) throw new IllegalArgumentException("topK 必须为正数");
        java.util.PriorityQueue<ScoredId> best = new java.util.PriorityQueue<>(Comparator.comparingDouble(ScoredId::score));
        for (ChunkVectorEntity row : repository.findAllForScan()) {
            double sim = dot(query, row.toFloats());
            if (!Double.isNaN(sim)) {
                best.offer(new ScoredId(row.getChunkId(), sim));
                if (best.size() > topK) best.poll();
            }
        }
        List<ScoredId> results = new ArrayList<>(best);
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
        if (a.length != b.length) throw new IllegalStateException("向量维度不匹配，请使用当前模型重新导入知识库");
        int n = a.length;
        double sum = 0;
        for (int i = 0; i < n; i++) sum += (double) a[i] * b[i];
        return sum; // 两向量均已归一化，点积即余弦
    }
}
