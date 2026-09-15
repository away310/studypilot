package com.studypilot.infrastructure.vector;

import com.studypilot.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CosineVectorStoreTest {
    @Test void actualStoreRanksAndLimitsDatabaseVectors() {
        var repository = mock(ChunkVectorRepository.class);
        when(repository.findAllForScan()).thenReturn(List.of(
                new ChunkVectorEntity("a", new float[]{1, 0}),
                new ChunkVectorEntity("b", new float[]{0, 1}),
                new ChunkVectorEntity("c", new float[]{-1, 0})));
        var hits = new CosineVectorStore(repository).search(new float[]{3, 0}, 2);
        assertEquals(List.of("a", "b"), hits.stream().map(VectorStore.ScoredId::chunkId).toList());
        assertEquals(1, hits.getFirst().score(), 1e-6);
    }

    @Test void dimensionMismatchFailsInsteadOfSilentlyTruncating() {
        assertThrows(IllegalStateException.class, () -> CosineVectorStore.dot(new float[]{1}, new float[]{1, 0}));
    }

    @Test void normalizeMakesDotProductEqualCosine() {
        float[] n = CosineVectorStore.normalize(new float[]{3, 4});
        assertEquals(1, CosineVectorStore.dot(n, n), 1e-6);
    }
}
