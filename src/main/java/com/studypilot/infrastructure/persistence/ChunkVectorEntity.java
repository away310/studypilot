package com.studypilot.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 文本块向量（SQLite 自研轻量向量存储的一行）。 */
@Entity
@Table(name = "chunk_vector")
public class ChunkVectorEntity {

    @Id
    private String chunkId;

    /** 归一化后的向量，按 float 小端序存为字节数组（SQLite 以 BLOB 存储）。 */
    @Column(name = "vector", columnDefinition = "BLOB")
    @JdbcTypeCode(SqlTypes.VARBINARY)
    private byte[] vector;

    private int dimension;

    protected ChunkVectorEntity() {}

    public ChunkVectorEntity(String chunkId, float[] normalizedVector) {
        this.chunkId = chunkId;
        this.vector = floatsToBytes(normalizedVector);
        this.dimension = normalizedVector.length;
    }

    public String getChunkId() { return chunkId; }
    public int getDimension() { return dimension; }

    public float[] toFloats() {
        return bytesToFloats(vector);
    }

    static byte[] floatsToBytes(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = Float.floatToIntBits(floats[i]);
            bytes[i * 4] = (byte) (bits & 0xFF);
            bytes[i * 4 + 1] = (byte) ((bits >> 8) & 0xFF);
            bytes[i * 4 + 2] = (byte) ((bits >> 16) & 0xFF);
            bytes[i * 4 + 3] = (byte) ((bits >> 24) & 0xFF);
        }
        return bytes;
    }

    static float[] bytesToFloats(byte[] bytes) {
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = (bytes[i * 4] & 0xFF)
                    | ((bytes[i * 4 + 1] & 0xFF) << 8)
                    | ((bytes[i * 4 + 2] & 0xFF) << 16)
                    | ((bytes[i * 4 + 3] & 0xFF) << 24);
            floats[i] = Float.intBitsToFloat(bits);
        }
        return floats;
    }
}
