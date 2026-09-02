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
import org.springframework.transaction.annotation.Transactional;

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

    public IngestService(ParserRouter parserRouter,
                         StructureAwareSplitter splitter,
                         EmbeddingModel embeddingModel,
                         VectorStore vectorStore,
                         KbDocumentRepository documentRepository,
                         KbChunkRepository chunkRepository,
                         ChunkIndexService indexService) {
        this.parserRouter = parserRouter;
        this.splitter = splitter;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.indexService = indexService;
    }

    @Transactional
    public DocumentMeta ingest(String fileName, InputStream in) throws IOException {
        String type = ParserRouter.normalizeType(fileName);
        ParsedDocument parsed = parserRouter.parse(fileName, type, in);

        List<StructureAwareSplitter.Section> sections = splitter.split(parsed);

        // 幂等：同名同类型先清理
        List<KbDocumentEntity> existing = documentRepository.findAll().stream()
                .filter(d -> d.getName().equals(fileName))
                .toList();
        for (KbDocumentEntity doc : existing) {
            deleteDocument(doc.getId());
        }

        KbDocumentEntity entity = new KbDocumentEntity();
        entity.setName(fileName);
        entity.setSourceType(type);
        entity.setChunkCount(sections.size());
        documentRepository.save(entity);

        // 批量向量化（DashScope embedding 接受文本列表；超长文本在此前切分已避免）
        List<TextChunk> chunks = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();
        List<String> texts = new ArrayList<>();

        int seq = 0;
        for (StructureAwareSplitter.Section section : sections) {
            String content = section.text().isBlank() ? section.headingPath() : section.text();
            TextChunk chunk = TextChunk.of(entity.getId(), fileName, section.headingPath(), seq++, content);
            chunks.add(chunk);
            chunkIds.add(chunk.id());
            texts.add(content);
        }

        // 分批 embedding（每批 16 条，规避 DashScope 单次上限与超时）
        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += 16) {
            List<String> batch = texts.subList(i, Math.min(texts.size(), i + 16));
            var resp = embeddingModel.call(new EmbeddingRequest(batch, null));
            for (var ed : resp.getResults()) {
                vectors.add(ed.getOutput());
            }
        }

        // 落库：先块，后向量
        chunkRepository.saveAll(chunks.stream().map(KbChunkEntity::new).toList());
        vectorStore.upsert(chunkIds, vectors);

        // 同步重建关键词索引（数据量小，毫秒级）
        indexService.refresh();

        return new DocumentMeta(entity.getId(), fileName, type, chunks.size(), entity.getCreatedAt());
    }

    @Transactional
    public void deleteDocument(Long docId) {
        List<TextChunk> chunks = chunkRepository.findModelsByDocId(docId);
        if (!chunks.isEmpty()) {
            List<String> ids = chunks.stream().map(TextChunk::id).toList();
            vectorStore.deleteByChunkIds(ids);
            chunkRepository.deleteByDocId(docId);
        }
        documentRepository.deleteById(docId);
        indexService.refresh();
    }

    public List<DocumentMeta> listDocuments() {
        return documentRepository.findAll().stream()
                .map(d -> new DocumentMeta(d.getId(), d.getName(), d.getSourceType(), d.getChunkCount(), d.getCreatedAt()))
                .toList();
    }
}