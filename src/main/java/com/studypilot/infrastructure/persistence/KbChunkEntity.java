package com.studypilot.infrastructure.persistence;

import com.studypilot.model.TextChunk;
import jakarta.persistence.*;

/** 切分后的文本块（落库，保证重启后知识库仍在）。 */
@Entity
@Table(name = "kb_chunk", indexes = {
        @Index(name = "idx_chunk_doc", columnList = "docId")
})
public class KbChunkEntity {

    @Id
    private String id; // docId-seq

    private Long docId;

    private String docName;

    @Column(columnDefinition = "TEXT")
    private String headingPath;

    private int seq;

    @Column(length = 8000)
    private String content;

    protected KbChunkEntity() {}

    public KbChunkEntity(TextChunk chunk) {
        this.id = chunk.id();
        this.docId = chunk.docId();
        this.docName = chunk.docName();
        this.headingPath = chunk.headingPath();
        this.seq = chunk.seq();
        this.content = chunk.content();
    }

    public TextChunk toModel() {
        return new TextChunk(id, docId, docName, headingPath, seq, content);
    }

    public String getId() { return id; }
    public Long getDocId() { return docId; }
    public String getDocName() { return docName; }
    public String getHeadingPath() { return headingPath; }
    public int getSeq() { return seq; }
    public String getContent() { return content; }
}
