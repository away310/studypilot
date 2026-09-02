package com.studypilot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChunkVectorRepository extends JpaRepository<ChunkVectorEntity, String> {

    void deleteByChunkIdIn(List<String> chunkIds);

    /** 全量向量扫描（自研轻量向量库：块数 ≤ 数千时内存余弦足够快，接口保留换 Qdrant 的扩展位）。 */
    @Query("select v from ChunkVectorEntity v")
    List<ChunkVectorEntity> findAllForScan();
}
