package com.studypilot.adapter.web;

import com.studypilot.application.ingest.IngestService;
import com.studypilot.model.DocumentMeta;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final IngestService ingestService;

    public KbController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    /** 上传文档入库（支持 markdown/txt/pdf）。 */
    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
        }
        try (var in = file.getInputStream()) {
            DocumentMeta meta = ingestService.ingest(file.getOriginalFilename(), in);
            return ResponseEntity.ok(meta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "解析失败: " + e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public List<DocumentMeta> list() {
        return ingestService.listDocuments();
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<?> delete(@PathVariable Long docId) {
        ingestService.deleteDocument(docId);
        return ResponseEntity.ok(Map.of("deleted", docId));
    }
}
