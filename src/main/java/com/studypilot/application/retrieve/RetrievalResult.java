package com.studypilot.application.retrieve;

import com.studypilot.model.RetrievalHit;
import com.studypilot.model.TextChunk;

import java.util.List;

/** 检索结果：命中的块（按混合分降序）+ 是否达到拒答置信度。 */
public record RetrievalResult(
        String query,
        List<RetrievalHit> hits,
        boolean confident   // 最高分达到拒答阈值则为 true
) {
}
