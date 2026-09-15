package com.studypilot.adapter.web;

import com.studypilot.application.answer.AnswerService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AnswerService answerService;

    public ChatController(AnswerService answerService) {
        this.answerService = answerService;
    }

    /** RAG 问答（普通 JSON 返回；Web 页使用 /api/chat/stream SSE）。 */
    @PostMapping
    public AnswerService.AnswerResult ask(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        question = question == null ? "" : question.strip();
        if (question.isEmpty() || question.length() > 4000) {
            throw new IllegalArgumentException("问题需为 1 到 4000 个字符");
        }
        return answerService.answer(question);
    }
}
