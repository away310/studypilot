package com.studypilot.application.eval;

import java.util.List;

/** 一种检索模式的整体评测结果（JSON 序列化给前端/报告用）。 */
public record EvalReport(
        String mode,              // vector / keyword / hybrid
        int total,
        double recallAt5,         // 兼容旧 API：值为 Hit@5，不是多文档 Recall
        double hitAt1,            // 期望文档出现在 top-1 的比例
        List<Detail> details
) {
    @com.fasterxml.jackson.annotation.JsonProperty("hitAt5")
    public double hitAt5() { return recallAt5; }

    public record Detail(String question, boolean hit, List<String> topDocs) {
    }
}
