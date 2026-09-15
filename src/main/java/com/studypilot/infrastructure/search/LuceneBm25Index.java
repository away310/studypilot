package com.studypilot.infrastructure.search;

import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Lucene BM25 关键词检索（内存索引，中文 SmartChinese 分词）。
 *
 * 索引在每次入库/删除后重建，新索引构建成功后才替换旧索引；
 * BM25 原始分由检索层使用固定饱和常数映射后加权融合。
 */
@Component
public class LuceneBm25Index {

    private Directory directory;
    private IndexWriter writer;
    private long version = 0;

    public LuceneBm25Index() {
        try {
            this.directory = new ByteBuffersDirectory();
            this.writer = createWriter(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private IndexWriter createWriter(Directory dir) throws IOException {
        return new IndexWriter(dir, new IndexWriterConfig(new SmartChineseAnalyzer()));
    }

    /** 全量重建（入库/删除后调用，简单可靠）。 */
    public synchronized void rebuild(Map<String, String> chunkIdToText) {
        Directory nextDirectory = new ByteBuffersDirectory();
        IndexWriter nextWriter = null;
        try {
            nextWriter = createWriter(nextDirectory);
            for (Map.Entry<String, String> e : chunkIdToText.entrySet()) {
                Document doc = new Document();
                doc.add(new StringField("id", e.getKey(), Field.Store.YES));
                doc.add(new TextField("text", e.getValue(), Field.Store.NO));
                nextWriter.addDocument(doc);
            }
            nextWriter.commit();
            IndexWriter oldWriter = writer;
            Directory oldDirectory = directory;
            writer = nextWriter;
            directory = nextDirectory;
            nextWriter = null;
            version++;
            oldWriter.close();
            oldDirectory.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            if (nextWriter != null) {
                try { nextWriter.close(); nextDirectory.close(); }
                catch (IOException ignored) { }
            }
        }
    }

    /** BM25 检索，返回 chunkId → 原始 BM25 分（降序）。 */
    public synchronized LinkedHashMap<String, Double> search(String queryText, int topN) {
        LinkedHashMap<String, Double> out = new LinkedHashMap<>();
        if (queryText == null || queryText.isBlank()) {
            return out;
        }
        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = new QueryParser("text", new SmartChineseAnalyzer()).parse(QueryParser.escape(queryText));
            TopDocs topDocs = searcher.search(query, topN);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(sd.doc);
                out.put(doc.get("id"), (double) sd.score);
            }
        } catch (Exception e) {
            // 查询解析失败（如全停用词）时返回空，走纯向量通道
            return out;
        }
        return out;
    }

    public long version() {
        return version;
    }
}
