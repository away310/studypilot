package com.studypilot.application.answer;

import com.studypilot.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnswerServiceTest {
    private final AnswerService service = new AnswerService(null, null);
    private final List<RetrievalHit> hits = List.of(
            new RetrievalHit(TextChunk.of(1L, "one.md", "第一节", 0, "原文一"), .9, .9, 0, 1, 0),
            new RetrievalHit(TextChunk.of(2L, "two.md", "第二节", 0, "原文二"), .8, .8, 0, 2, 0));

    @Test void keepsOnlyUsedCitationsAndRenumbersInOrder() {
        var answer = service.validateAnswer("问题", "结论 [2]，补充 [2]。", hits);
        assertFalse(answer.rejected());
        assertEquals("结论 [1]，补充 [1]。", answer.answer());
        assertEquals(List.of(hits.get(1)), answer.citations());
    }

    @Test void rejectsMissingInvalidOrOverflowedCitations() {
        for (String text : List.of("无来源结论", "结论 [0]", "结论 [3]", "结论 [99999999999999999]", "")) {
            assertTrue(service.validateAnswer("问题", text, hits).rejected(), text);
            assertTrue(service.validateAnswer("问题", text, hits).citations().isEmpty());
        }
    }

    @Test void respectsModelAbstention() {
        assertTrue(service.validateAnswer("问题", "资料中未涉及", hits).rejected());
    }
}
