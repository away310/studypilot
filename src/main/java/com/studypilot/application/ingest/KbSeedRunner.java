package com.studypilot.application.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 启动时把 ./kb 下的示例文档自动导入（已存在同名文档则跳过，避免重复向量化）。
 */
@Component
@Order(10)
public class KbSeedRunner implements ApplicationRunner {

    private final IngestService ingestService;

    @Value("${app.kb.default-dir:./kb}")
    private String kbDir;

    @Value("${app.kb.auto-seed:true}")
    private boolean autoSeed;

    public KbSeedRunner(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!autoSeed) {
            return;
        }
        Path dir = Path.of(kbDir);
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<String> imported = ingestService.listDocuments().stream()
                .map(d -> d.name()).toList();

        try (var files = Files.list(dir)) {
            List<Path> docs = files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".md") || n.endsWith(".markdown") || n.endsWith(".txt") || n.endsWith(".pdf");
                    })
                    .toList();
            for (Path doc : docs) {
                String name = doc.getFileName().toString();
                if (imported.contains(name)) {
                    continue; // 已入库，跳过
                }
                try (InputStream in = Files.newInputStream(doc)) {
                    ingestService.ingest(name, in);
                    System.out.println("[seed] imported: " + name);
                } catch (Exception e) {
                    System.err.println("[seed] import failed: " + name + " -> " + e.getMessage());
                }
            }
        }
    }
}
