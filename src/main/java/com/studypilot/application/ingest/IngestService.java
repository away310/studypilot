package com.studypilot.application.ingest;

import com.studypilot.application.index.ChunkIndexService;
import com.studypilot.infrastructure.parser.ParsedDocument;
import com.studypilot.infrastructure.parser.ParserRouter;
import com.studypilot.infrastructure.persistence.KbChunkEntity;
import com.studypilot.infrastructure.persistence.KbChunkRepository;
import com.studypilot.infrastructure.persistence.KbDocumentEntity;
import com.studypilot.infrastructure.persistence.KbDocumentRepository;
import com.studypilot.infrastructure.splitter.StructureAwareSplitter;
import com.studypilot.infrastructure.vector.VectorStore;
import com.studypilot.model.DocumentMeta;
import com.studypilot.model.TextChunk;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 入库流水线：解析 → 结构感知切分 → 批量向量化 → 元数据/块/向量落库。
 * 同一文件重复上传时先删除旧版本（幂等）。
 */
@Service
public class IngestService {

    private final ParserRouter parserRouter;
    private final StructureAwareSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;
    private final ChunkIndexService indexService;

    private final TransactionTemplate transaction;

    public IngestService(ParserRouter parserRouter,
                         StructureAwareSplitter splitter,
                         EmbeddingModel embeddingModel,
                         VectorStore vectorStore,
                         KbDocumentRepository documentRepository,
                         KbChunkRepository chunkRepository,
                         ChunkIndexService indexService, PlatformTransactionManager transactionManager) {
        this.parserRouter = parserRouter;
        this.splitter = splitter;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.indexService = indexService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public DocumentMeta ingest(String fileName, InputStream in) throws IOException {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("文件名不能为空");
        String type = ParserRouter.normalizeType(fileName);
        ParsedDocument parsed = parserRouter.parse(fileName, type, in);
        List<StructureAwareSplitter.Section> sections = splitter.split(parsed);
        if (sections.isEmpty()) throw new IllegalArgumentException("文档没有可索引的正文");

        // 远程调用全部在数据库事务外完成，失败时旧版本保持原样。
        List<String> texts = sections.stream()
                .map(s -> TextChunk.searchText(s.headingPath(), s.text())).toList();
        List<float[]> vectors = new ArrayList<>();
        Integer dimension = null;
        for (int i = 0; i < texts.size(); i += 16) {
            List<String> batch = texts.subList(i, Math.min(texts.size(), i + 16));
            var response = embeddingModel.call(new EmbeddingRequest(batch, null));
            if (response == null || response.getResults().size() != batch.size()) {
                throw new IllegalStateException("向量服务返回数量与文本数量不一致");
            }
            float[][] ordered = new float[batch.size()][];
            for (var embedding : response.getResults()) {
                int index = embedding.getIndex();
                float[] vector = embedding.getOutput();
                if (index < 0 || index >= ordered.length || ordered[index] != null
                        || vector == null || vector.length == 0) {
                    throw new IllegalStateException("向量服务返回无效索引或空向量");
                }
                if (dimension == null) dimension = vector.length;
                if (dimension != vector.length) throw new IllegalStateException("向量维度不一致");
                double norm = 0;
                for (float value : vector) {
                    if (!Float.isFinite(value)) throw new IllegalStateException("向量包含非有限值");
                    norm += (double) value * value;
                }
                if (norm == 0) throw new IllegalStateException("向量不能全为零");
                ordered[index] = vector;
            }
            vectors.addAll(java.util.Arrays.asList(ordered));
        }
        return persist(fileName, type, sections, vectors);
    }

    // 单进程个人知识库：串行替换/删除，避免同名上传互相覆盖事务中的中间状态。
    private synchronized DocumentMeta persist(String fileName, String type,
            List<StructureAwareSplitter.Section> sections, List<float[]> vectors) {
        DocumentMeta result = transaction.execute(status -> {
            documentRepository.findAll().stream().filter(d -> d.getName().equals(fileName))
                    .toList().forEach(d -> deleteRows(d.getId()));
            KbDocumentEntity entity = new KbDocumentEntity();
            entity.setName(fileName);
            entity.setSourceType(type);
            entity.setChunkCount(sections.size());
            documentRepository.save(entity);
            List<TextChunk> chunks = new ArrayList<>();
            for (int i = 0; i < sections.size(); i++) {
                var section = sections.get(i);
                chunks.add(TextChunk.of(entity.getId(), fileName, section.headingPath(), i, section.text()));
            }
            chunkRepository.saveAll(chunks.stream().map(KbChunkEntity::new).toList());
            vectorStore.upsert(chunks.stream().map(TextChunk::id).toList(), vectors);
            indexService.invalidateAfterCommit();
            return new DocumentMeta(entity.getId(), fileName, type, chunks.size(), entity.getCreatedAt());
        });
        indexService.refreshIfDirty();
        return result;
    }

    public synchronized void deleteDocument(Long docId) {
        transaction.executeWithoutResult(status -> {
            deleteRows(docId);
            indexService.invalidateAfterCommit();
        });
        indexService.refreshIfDirty();
    }

    private void deleteRows(Long docId) {
        List<TextChunk> chunks = chunkRepository.findModelsByDocId(docId);
        if (!chunks.isEmpty()) {
            vectorStore.deleteByChunkIds(chunks.stream().map(TextChunk::id).toList());
            chunkRepository.deleteByDocId(docId);
        }
        documentRepository.deleteById(docId);
    }

    public List<DocumentMeta> listDocuments() {
        return documentRepository.findAll().stream()
                .map(d -> new DocumentMeta(d.getId(), d.getName(), d.getSourceType(), d.getChunkCount(), d.getCreatedAt()))
                .toList();
    }
}