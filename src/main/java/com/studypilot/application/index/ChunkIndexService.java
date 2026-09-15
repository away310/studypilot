package com.studypilot.application.index;

import com.studypilot.infrastructure.persistence.KbChunkEntity;
import com.studypilot.infrastructure.persistence.KbChunkRepository;
import com.studypilot.infrastructure.search.LuceneBm25Index;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * BM25 关键词索引管理器：启动时与入库/删除后全量重建（数据库提交后刷新，失败时下一次检索重试）。
 */
@Component
public class ChunkIndexService implements ApplicationRunner {

    private final KbChunkRepository chunkRepository;
    private final LuceneBm25Index bm25Index;

    public ChunkIndexService(KbChunkRepository chunkRepository, LuceneBm25Index bm25Index) {
        this.chunkRepository = chunkRepository;
        this.bm25Index = bm25Index;
    }

    private volatile boolean dirty = true;

    public void invalidateAfterCommit() {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { markDirty(); }
                });
    }

    private synchronized void markDirty() { dirty = true; }

    public synchronized void refreshIfDirty() {
        if (dirty) refresh();
    }

    public synchronized void refresh() {
        dirty = true;
        Map<String, String> idToText = new HashMap<>();
        for (KbChunkEntity chunk : chunkRepository.findAll()) {
            idToText.put(chunk.getId(), com.studypilot.model.TextChunk.searchText(chunk.getHeadingPath(), chunk.getContent()));
        }
        bm25Index.rebuild(idToText);
        dirty = false;
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }
}
