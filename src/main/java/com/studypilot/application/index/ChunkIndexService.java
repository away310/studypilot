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
 * BM25 关键词索引管理器：启动时与入库/删除后全量重建（数据量小，重建毫秒级）。
 */
@Component
public class ChunkIndexService implements ApplicationRunner {

    private final KbChunkRepository chunkRepository;
    private final LuceneBm25Index bm25Index;

    public ChunkIndexService(KbChunkRepository chunkRepository, LuceneBm25Index bm25Index) {
        this.chunkRepository = chunkRepository;
        this.bm25Index = bm25Index;
    }

    public synchronized void refresh() {
        Map<String, String> idToText = new HashMap<>();
        for (KbChunkEntity chunk : chunkRepository.findAll()) {
            idToText.put(chunk.getId(), chunk.getContent());
        }
        bm25Index.rebuild(idToText);
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }
}
