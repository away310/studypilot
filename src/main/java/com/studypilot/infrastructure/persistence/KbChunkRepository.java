package com.studypilot.infrastructure.persistence;

import com.studypilot.model.TextChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbChunkRepository extends JpaRepository<KbChunkEntity, String> {

    List<KbChunkEntity> findByDocId(Long docId);

    void deleteByDocId(Long docId);

    long countByDocId(Long docId);

    /** 供问答渲染时按 id 批量取块（顺序无关，由调用方排序）。 */
    @Query("select c from KbChunkEntity c where c.id in :ids")
    List<KbChunkEntity> findByIds(@Param("ids") List<String> ids);

    default List<TextChunk> findModelsByDocId(Long docId) {
        return findByDocId(docId).stream().map(KbChunkEntity::toModel).toList();
    }
}
