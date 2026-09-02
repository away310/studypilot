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
        String question = body.getOrDefault("question", "").strip();
        if (question.isEmpty()) {
            return new AnswerService.AnswerResult("", "问题不能为空", java.util.List.of(), true);
        }
        return answerService.answer(question);
    }
}
