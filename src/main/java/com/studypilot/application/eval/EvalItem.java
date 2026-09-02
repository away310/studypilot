package com.studypilot.application.eval;

import java.util.List;

/** 一条评测样本：问题 + 期望命中的文档（评估检索是否召回相关内容）。 */
public record EvalItem(
        String question,
        List<String> expectedDocNames, // 至少命中其一即算正例
        String referenceAnswer        // 用于答案质量评估（可选）
) {
}
