package com.studypilot;

import com.studypilot.application.answer.AnswerService;
import com.studypilot.application.eval.EvalItem;
import com.studypilot.application.eval.EvalReport;
import com.studypilot.application.eval.EvalService;
import com.studypilot.application.index.ChunkIndexService;
import com.studypilot.application.ingest.IngestService;
import com.studypilot.application.retrieve.RetrievalService;
import com.studypilot.application.retrieve.RetrievalResult;
import com.studypilot.infrastructure.persistence.ChunkVectorRepository;
import com.studypilot.infrastructure.persistence.KbChunkRepository;
import com.studypilot.infrastructure.persistence.KbDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 端到端集成测试（不依赖真实 DashScope Key）：
 * 用确定性 mock embedding（词袋哈希向量）与 mock chat 验证整条 RAG 管线，
 * 覆盖：入库 → 检索 → 评测 → 问答 → 拒答。
 * mock 只验证管线行为，不证明真实模型的回答质量。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:studypilot-test?mode=memory&cache=shared",
        "app.kb.auto-seed=false",
        "spring.ai.dashscope.api-key=mock",
        // mock 词袋向量的余弦值天然低于真实 embedding，测试用较低阈值验证"命中→不拒/无关→拒"的逻辑
        "app.answer.reject-threshold=0.08"
})
class StudyPilotE2eFlowTest {

    @Autowired IngestService ingestService;
    @Autowired RetrievalService retrievalService;
    @Autowired AnswerService answerService;
    @Autowired EvalService evalService;
    @Autowired KbDocumentRepository documentRepository;
    @Autowired KbChunkRepository chunkRepository;
    @Autowired ChunkVectorRepository vectorRepository;
    @Autowired ChunkIndexService indexService;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    ChatClient chatClient;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.studypilot.infrastructure.search.LuceneBm25Index bm25;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.studypilot.infrastructure.vector.CosineVectorStore vectorStore;


    @BeforeEach
    void setUp() throws Exception {
        clearAllData();

        // 确定性词袋向量：同一 token 命中越多，向量越接近（可复现、无需外部服务）
        Mockito.when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest req = inv.getArgument(0);
            List<Embedding> results = new ArrayList<>();
            int idx = 0;
            for (String text : req.getInstructions()) {
                results.add(new Embedding(bagOfWords(text), idx++));
            }
            return new EmbeddingResponse(results);
        });

        // mock chat：返回"基于 [1] 的模拟回答"，验证 prompt/引用链路
        ChatClient.ChatClientRequestSpec spec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = Mockito.mock(ChatClient.CallResponseSpec.class);
        ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);

        Mockito.when(chatClient.prompt()).thenReturn(spec);
        Mockito.when(spec.system(Mockito.anyString())).thenReturn(spec);
        Mockito.when(spec.user(Mockito.anyString())).thenReturn(spec);
        Mockito.when(spec.call()).thenReturn(callSpec);
        Mockito.when(callSpec.content()).thenReturn("这是模拟回答，依据 [1] 号资料。");
        Mockito.when(spec.stream()).thenReturn(streamSpec);
        Mockito.when(streamSpec.content()).thenReturn(Flux.just("这是", "模拟回答", "。"));
    }

    /** 清空库表并重建 BM25 索引，保证每个用例从空库开始。 */
    private void clearAllData() {
        chunkRepository.deleteAll();
        vectorRepository.deleteAll();
        documentRepository.deleteAll();
        indexService.refresh();
    }

    /** 三篇示例文档入库（与 kb/ 目录一致）。 */
    private void ingestSampleDocs() throws Exception {
        ingest("spring-ai-rag-notes.md", """
                # Spring AI RAG 实现要点
                ## 混合检索
                向量检索加 BM25 关键词检索双通道召回后融合排序。
                ## 切分
                结构感知切分按标题层级切块，保留章节上下文。
                ## 评测
                Recall@5 衡量期望文档出现在前五条的比例。
                """);
        ingest("agent-function-calling.md", """
                # Agent 与 Function Calling
                ## 工具注册表
                工具多起来后需要一个注册中心统一管理。
                ## 调用流程
                模型返回结构化 JSON 工具名与参数，应用执行后回传结果。
                """);
        ingest("java21-concurrency.md", """
                # Java 21 并发
                ## 线程池
                ThreadPoolExecutor 的核心参数有 corePoolSize 与 workQueue。
                ## 虚拟线程
                高并发 IO 密集型场景适合用虚拟线程。
                """);
        // 评测集覆盖的全部 kb 文档（保持与 src/main/resources/eval-set.json 的期望一致）
        java.nio.file.Path kb = java.nio.file.Path.of("./kb");
        try (var files = java.nio.file.Files.list(kb)) {
            for (java.nio.file.Path p : files.toList()) {
                String name = p.getFileName().toString();
                if (!name.equals("spring-ai-rag-notes.md")
                        && !name.equals("agent-function-calling.md")
                        && !name.equals("java21-concurrency.md")) {
                    ingest(name, java.nio.file.Files.readString(p));
                }
            }
        }
    }

    private void ingest(String name, String content) throws Exception {
        ingestService.ingest(name, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void fullPipeline_ingestRetrieveEvalAnswer() throws Exception {
        ingestSampleDocs();

        // 1) 检索：混合模式下能命中 RAG 文档
        RetrievalResult result = retrievalService.retrieve("混合检索是哪两条通道？怎么融合？");
        assertFalse(result.hits().isEmpty(), "应有检索命中");
        assertEquals("spring-ai-rag-notes.md", result.hits().get(0).chunk().docName(),
                "top-1 应为 RAG 笔记");

        // 2) 评测：三组结构完整，混合 Hit@5 至少覆盖半数样本
        List<EvalItem> items = evalService.loadEvalSet();
        List<EvalReport> reports = evalService.runAll(items);
        assertEquals(3, reports.size());
        for (EvalReport r : reports) {
            assertEquals(items.size(), r.total(), "内置评测集条目数");
            assertTrue(r.recallAt5() >= 0 && r.recallAt5() <= 1);
        }
        EvalReport hybrid = reports.stream().filter(r -> r.mode().equals("hybrid")).findFirst().orElseThrow();
        assertTrue(hybrid.recallAt5() >= 0.5,
                "mock 词袋向量下混合检索也应命中多数，实际=" + hybrid.recallAt5());

        // 3) 问答：不拒答且返回引用
        AnswerService.AnswerResult answer = answerService.answer("混合检索怎么融合排序？");
        assertFalse(answer.rejected(), "命中充分不应拒答");
        assertFalse(answer.citations().isEmpty(), "回答应带引用");
        assertTrue(answer.answer().contains("模拟回答"), "应走到 LLM 生成（mock）");
    }

    @Test
    void rejectsWhenKnowledgeBaseHasNoAnswer() throws Exception {
        // 空知识库（不 ingest）→ 检索必然无命中 → 应拒答且不调用 LLM
        AnswerService.AnswerResult answer = answerService.answer("苹果公司下一季度财报预测是什么？");
        assertTrue(answer.rejected(), "空知识库应拒答");
        assertTrue(answer.answer().contains("没有找到"), "拒答文案应提示无相关内容");
        Mockito.verify(chatClient, Mockito.never()).prompt();
    }

    @Test
    void failedEmbeddingReplacementPreservesOldDocumentAndIndex() throws Exception {
        ingest("stable.md", "# 原始标题\n稳定内容独特关键词");
        var original = ingestService.listDocuments().getFirst();
        Mockito.when(embeddingModel.call(any(EmbeddingRequest.class))).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> ingest("stable.md", "# 新版本\n其他内容"));
        assertEquals(original.id(), ingestService.listDocuments().getFirst().id());
        assertFalse(retrievalService.retrieve("独特关键词", RetrievalService.Mode.KEYWORD).hits().isEmpty());
    }

    @Test
    void failedDatabaseWriteRollsBackWithoutPublishingIndex() throws Exception {
        ingest("stable.md", "# 原始标题\n稳定内容独特关键词");
        var original = ingestService.listDocuments().getFirst();
        long version = bm25.version();
        Mockito.doThrow(new IllegalStateException("write failure")).when(vectorStore).upsert(any(), any());
        assertThrows(IllegalStateException.class, () -> ingest("stable.md", "# 新版本\n替代内容"));
        assertEquals(original.id(), ingestService.listDocuments().getFirst().id());
        assertEquals(version, bm25.version());
        assertFalse(retrievalService.retrieve("独特关键词", RetrievalService.Mode.KEYWORD).hits().isEmpty());
    }

    @Test
    void failedIndexRefreshRetriesOnNextSearch() throws Exception {
        Mockito.doThrow(new IllegalStateException("index unavailable")).doCallRealMethod().when(bm25).rebuild(any());
        assertThrows(IllegalStateException.class, () -> ingest("retry.md", "# 可恢复索引\n刷新重试内容"));
        assertEquals(1, ingestService.listDocuments().size(), "数据库已提交");
        assertFalse(retrievalService.retrieve("可恢复索引", RetrievalService.Mode.KEYWORD).hits().isEmpty());
    }

    @Test
    void headingsParticipateInBothRetrievalChannelsAndReplacementDeletesOldChunks() throws Exception {
        ingest("headings.md", "# 独特标题术语\n正文没有该关键词");
        Mockito.verify(embeddingModel).call(Mockito.argThat(request ->
                request.getInstructions().getFirst().contains("独特标题术语\n正文")));
        var hits = retrievalService.retrieve("独特标题术语", RetrievalService.Mode.KEYWORD).hits();
        assertEquals("headings.md", hits.getFirst().chunk().docName());
        ingest("headings.md", "# 全新版本\n替代文字");
        assertEquals(1, ingestService.listDocuments().size());
        assertEquals(1, vectorRepository.count());
        assertTrue(retrievalService.retrieve("独特标题术语", RetrievalService.Mode.KEYWORD).hits().isEmpty());
        ingestService.deleteDocument(ingestService.listDocuments().getFirst().id());
        assertEquals(0, vectorRepository.count());
        assertTrue(retrievalService.retrieve("全新版本", RetrievalService.Mode.KEYWORD).hits().isEmpty());
    }

    @Test
    void rejectsUnrelatedQuestionWithNonEmptyKnowledgeBaseWithoutCallingChat() throws Exception {
        Mockito.when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(
                new EmbeddingResponse(List.of(new Embedding(new float[]{1, 0}, 0))));
        ingest("redis.md", "# Redis\n缓存雪崩处理");
        Mockito.when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(
                new EmbeddingResponse(List.of(new Embedding(new float[]{0, 1}, 0))));
        assertTrue(answerService.answer("明天的天气怎么样").rejected());
        Mockito.verify(chatClient, Mockito.never()).prompt();
    }

    @Test
    void malformedEmbeddingDoesNotReplaceDocument() throws Exception {
        ingest("stable.md", "# 标题\n正文");
        long id = ingestService.listDocuments().getFirst().id();
        Mockito.when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of()));
        assertThrows(IllegalStateException.class, () -> ingest("stable.md", "# 标题\n新正文"));
        assertEquals(id, ingestService.listDocuments().getFirst().id());
    }

    /**
     * 字符级二值词袋向量：共享字符越多余弦越高。
     * 相比"每字符哈希计数"，二值向量避免了长文本计数主导导致的相似度虚高，
     * 使"相关查询（共享大部分字符）≈ 高相似度、无关查询 ≈ 低相似度"这一性质成立。
     */
    private static float[] bagOfWords(String text) {
        int dim = 4096;
        float[] vec = new float[dim];
        String cleaned = text.toLowerCase().replaceAll("[^\\u4e00-\\u9fa5a-z0-9]", "");
        // 中文逐字 + 英文逐字符统一映射为二值位（去重）
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < cleaned.length(); i++) {
            int h = (cleaned.charAt(i) * 131) & 0x7fffffff;
            seen.add(h % dim);
        }
        for (int bucket : seen) {
            vec[bucket] = 1f;
        }
        return vec; // CosineVectorStore 内部会做 L2 归一化
    }
}
