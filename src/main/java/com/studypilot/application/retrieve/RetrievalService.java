package com.studypilot.application.retrieve;

import com.studypilot.infrastructure.persistence.KbChunkEntity;
import com.studypilot.infrastructure.persistence.KbChunkRepository;
import com.studypilot.infrastructure.search.LuceneBm25Index;
import com.studypilot.infrastructure.vector.VectorStore;
import com.studypilot.model.RetrievalHit;
import com.studypilot.model.TextChunk;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索：向量（DashScope embedding 余弦）+ 关键词（Lucene BM25）双通道召回，
 * 按权重线性融合排序；另保留各通道得分用于调参与评测展示。
 *
 * 对比实验支持：
 * - 纯向量 / 纯关键词 / 混合 三档由 RetrievalMode 决定，供评测集批量验证。
 */
@Service
public class RetrievalService {

    public enum Mode { VECTOR, KEYWORD, HYBRID }

    private final com.studypilot.application.index.ChunkIndexService indexService;

    @Value("${app.retrieval.bm25-saturation:1.0}")
    private double bm25Saturation;

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final LuceneBm25Index bm25Index;
    private final KbChunkRepository chunkRepository;

    @Value("${app.retrieval.recall-candidates:20}")
    private int recallCandidates;

    @Value("${app.retrieval.top-k:5}")
    private int topK;

    @Value("${app.retrieval.vector-weight:0.6}")
    private double vectorWeight;

    @Value("${app.retrieval.keyword-weight:0.4}")
    private double keywordWeight;

    @Value("${app.answer.reject-threshold:0.35}")
    private double rejectThreshold;

    public RetrievalService(EmbeddingModel embeddingModel,
                            VectorStore vectorStore,
                            LuceneBm25Index bm25Index,
                            KbChunkRepository chunkRepository, com.studypilot.application.index.ChunkIndexService indexService) {
        this.indexService = indexService;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.bm25Index = bm25Index;
        this.chunkRepository = chunkRepository;
    }

    public RetrievalResult retrieve(String query, Mode mode) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("问题不能为空");
        if (mode != Mode.VECTOR) indexService.refreshIfDirty();
        List<RetrievalHit> hits = switch (mode) {
            case VECTOR -> vectorRetrieve(query);
            case KEYWORD -> keywordRetrieve(query);
            case HYBRID -> hybridRetrieve(query);
        };
        // 拒答判定基于「最大向量相似度」：语义上是否真有相关内容。
        // 不用混合分做阈值，避免 BM25 弱命中（如单个通用词）把分数虚高导致误判为有答案。
        double maxVectorScore = hits.stream().mapToDouble(RetrievalHit::vectorScore).max().orElse(0);
        boolean confident = !hits.isEmpty() && maxVectorScore >= rejectThreshold;
        return new RetrievalResult(query, hits, confident);
    }

    /** 混合检索（默认调用入口）。 */
    public RetrievalResult retrieve(String query) {
        return retrieve(query, Mode.HYBRID);
    }

    private List<RetrievalHit> vectorRetrieve(String query) {
        float[] q = embed(query);
        List<VectorStore.ScoredId> scored = vectorStore.search(q, recallCandidates);
        return toHits(scored, null);
    }

    private List<RetrievalHit> keywordRetrieve(String query) {
        LinkedHashMap<String, Double> scored = bm25Index.search(query, recallCandidates);
        return toHits(null, scored);
    }

    private List<RetrievalHit> hybridRetrieve(String query) {
        float[] q = embed(query);
        List<VectorStore.ScoredId> vecScored = vectorStore.search(q, recallCandidates);
        LinkedHashMap<String, Double> kwScored = bm25Index.search(query, recallCandidates);

        // BM25 使用固定饱和常数映射到 0~1（非概率）
        Map<String, Double> kwNorm = normalizeBm25(kwScored);
        Map<String, Double> vecScore = vecScored.stream()
                .collect(Collectors.toMap(VectorStore.ScoredId::chunkId, s -> clamp01(s.score())));
        Map<String, Double> vecRank = rankMap(vecScored.stream().map(VectorStore.ScoredId::chunkId).toList());
        Map<String, Double> kwRank = rankMap(new ArrayList<>(kwScored.keySet()));

        // 取并集融合
        Set<String> union = new LinkedHashSet<>();
        union.addAll(vecScored.stream().map(VectorStore.ScoredId::chunkId).toList());
        union.addAll(kwScored.keySet());

        List<ScoredText> merged = new ArrayList<>();
        for (String chunkId : union) {
            double vs = vecScore.getOrDefault(chunkId, 0.0);
            double ks = kwNorm.getOrDefault(chunkId, 0.0);
            // 融合分 = 加权和；单一通道命中也会保留（另一通道得分为 0）
            double score = vectorWeight * vs + keywordWeight * ks;
            merged.add(new ScoredText(chunkId, score, vs, ks,
                    vecRank.getOrDefault(chunkId, 0.0).intValue(),
                    kwRank.getOrDefault(chunkId, 0.0).intValue()));
        }
        merged.sort(Comparator.comparingDouble(ScoredText::score).reversed());
        List<ScoredText> top = merged.size() <= topK ? merged : merged.subList(0, topK);

        List<RetrievalHit> hits = new ArrayList<>();
        for (ScoredText s : top) {
            TextChunk chunk = loadChunk(s.chunkId);
            if (chunk != null) {
                hits.add(new RetrievalHit(chunk, s.score, s.vectorScore, s.keywordScore,
                        s.vectorRank, s.keywordRank));
            }
        }
        return hits;
    }

    /** 把向量检索结果转成 RetrievalHit（用于 VECTOR 模式与评测）。 */
    private List<RetrievalHit> toHits(List<VectorStore.ScoredId> vecScored,
                                      LinkedHashMap<String, Double> kwScored) {
        List<RetrievalHit> hits = new ArrayList<>();
        if (vecScored != null) {
            for (VectorStore.ScoredId s : vecScored) {
                TextChunk chunk = loadChunk(s.chunkId());
                if (chunk != null) {
                    hits.add(new RetrievalHit(chunk, clamp01(s.score()), clamp01(s.score()), 0,
                            hits.size() + 1, 0));
                }
            }
        }
        if (kwScored != null) {
            Map<String, Double> norm = normalizeBm25(kwScored);
            int rank = 0;
            for (Map.Entry<String, Double> e : kwScored.entrySet()) {
                TextChunk chunk = loadChunk(e.getKey());
                if (chunk != null) {
                    hits.add(new RetrievalHit(chunk, norm.get(e.getKey()), 0, norm.get(e.getKey()),
                            0, ++rank));
                }
            }
        }
        return hits.stream().limit(topK).toList();
    }

    private TextChunk loadChunk(String chunkId) {
        return chunkRepository.findById(chunkId).map(KbChunkEntity::toModel).orElse(null);
    }

    /**
     * BM25 平滑归一化到 0~1：norm = score / (score + k)。
     * 相比 min-max，不会因为"只有 1~2 个弱命中"就把最高分顶到 1.0，
     * 固定 k 避免单条弱命中被抬到 0.5；分数仍非跨语料可比的概率。
     */
    private Map<String, Double> normalizeBm25(LinkedHashMap<String, Double> raw) {
        Map<String, Double> out = new HashMap<>();
        if (raw.isEmpty()) {
            return out;
        }
        double k = Math.max(bm25Saturation, 1e-6);
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            double norm = e.getValue() / (e.getValue() + k);
            out.put(e.getKey(), norm);
        }
        return out;
    }

    private Map<String, Double> rankMap(List<String> ordered) {
        Map<String, Double> map = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            map.put(ordered.get(i), (double) (i + 1));
        }
        return map;
    }

    private float[] embed(String text) {
        var resp = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
        return resp.getResults().get(0).getOutput();
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private record ScoredText(String chunkId, double score, double vectorScore,
                              double keywordScore, int vectorRank, int keywordRank) {
    }
}
