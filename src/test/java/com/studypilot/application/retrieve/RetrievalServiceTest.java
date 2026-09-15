package com.studypilot.application.retrieve;

import com.studypilot.application.index.ChunkIndexService;
import com.studypilot.infrastructure.persistence.*;
import com.studypilot.infrastructure.search.LuceneBm25Index;
import com.studypilot.infrastructure.vector.VectorStore;
import com.studypilot.model.TextChunk;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetrievalServiceTest {
    @Test void singleWeakKeywordHitIsNotNormalizedToHalf() {
        var index = mock(LuceneBm25Index.class);
        var repo = mock(KbChunkRepository.class);
        var service = new RetrievalService(null, mock(VectorStore.class), index, repo, mock(ChunkIndexService.class));
        ReflectionTestUtils.setField(service, "topK", 1);
        ReflectionTestUtils.setField(service, "recallCandidates", 20);
        ReflectionTestUtils.setField(service, "bm25Saturation", 1.0);
        var scores = new LinkedHashMap<String, Double>();
        scores.put("1-0", .01);
        when(index.search("query", 20)).thenReturn(scores);
        when(repo.findById("1-0")).thenReturn(Optional.of(new KbChunkEntity(TextChunk.of(1L, "a", "", 0, "text"))));
        var result = service.retrieve("query", RetrievalService.Mode.KEYWORD);
        assertEquals(.01 / 1.01, result.hits().getFirst().keywordScore(), 1e-9);
        scores.put("2-0", .001);
        when(repo.findById("2-0")).thenReturn(Optional.of(new KbChunkEntity(TextChunk.of(2L, "b", "", 0, "text"))));
        assertEquals(1, service.retrieve("query", RetrievalService.Mode.KEYWORD).hits().size());
    }
}
