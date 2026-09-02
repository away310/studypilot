package com.studypilot.application.eval;

import java.util.List;

/** 一种检索模式的整体评测结果（JSON 序列化给前端/报告用）。 */
public record EvalReport(
        String mode,              // vector / keyword / hybrid
        int total,
        double recallAt5,         // 期望文档出现在 top-5 的比例
        double hitAt1,            // 期望文档出现在 top-1 的比例
        List<Detail> details
) {
    public record Detail(String question, boolean hit, List<String> topDocs) {
    }
}
