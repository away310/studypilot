package com.studypilot.application.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.application.retrieve.RetrievalService;
import com.studypilot.model.RetrievalHit;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 检索评测执行器：同一评测集分别跑 向量 / 关键词 / 混合 三种模式，
 * 输出文档级 Hit@5 与 Hit@1；前五个块命中任一期望文档即算成功。
 * referenceAnswer 仅供人工核对，当前不自动评估答案正确性。
 *
 * 评测集格式（eval/eval-set.json）：
 * [{"question":"...","expectedDocNames":["xxx.md"],"referenceAnswer":"..."}]
 */
@Service
public class EvalService {

    private final RetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    public EvalService(RetrievalService retrievalService, ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    /** 从内置评测集跑三组对比。 */
    public List<EvalReport> runDefaultEval() throws IOException {
        List<EvalItem> items = loadEvalSet();
        return runAll(items);
    }

    public List<EvalReport> runAll(List<EvalItem> items) {
        List<EvalReport> reports = new ArrayList<>();
        reports.add(run(items, RetrievalService.Mode.VECTOR));
        reports.add(run(items, RetrievalService.Mode.KEYWORD));
        reports.add(run(items, RetrievalService.Mode.HYBRID));
        return reports;
    }

    public EvalReport run(List<EvalItem> items, RetrievalService.Mode mode) {
        int hitAt1 = 0;
        int hitAt5 = 0;
        List<EvalReport.Detail> details = new ArrayList<>();

        for (EvalItem item : items) {
            var result = retrievalService.retrieve(item.question(), mode);
            List<String> topDocs = new ArrayList<>();
            int foundRank = -1;
            for (int i = 0; i < Math.min(result.hits().size(), 5); i++) {
                RetrievalHit hit = result.hits().get(i);
                String docName = hit.chunk().docName();
                topDocs.add(docName);
                if (foundRank < 0 && isExpected(docName, item.expectedDocNames())) {
                    foundRank = i; // 0-based
                }
            }
            if (foundRank == 0) hitAt1++;
            if (foundRank >= 0) hitAt5++;
            details.add(new EvalReport.Detail(item.question(), foundRank >= 0, topDocs));
        }

        return new EvalReport(mode.name().toLowerCase(), items.size(),
                items.isEmpty() ? 0 : (double) hitAt5 / items.size(),
                items.isEmpty() ? 0 : (double) hitAt1 / items.size(),
                details);
    }

    private boolean isExpected(String docName, List<String> expected) {
        return expected.stream().anyMatch(docName::equalsIgnoreCase);
    }

    public List<EvalItem> loadEvalSet() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/eval-set.json")) {
            if (in == null) {
                throw new IOException("未找到内置评测集 /eval-set.json");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        }
    }
}
