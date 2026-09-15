package com.studypilot.adapter.web;

import com.studypilot.application.answer.AnswerService;
import com.studypilot.application.retrieve.RetrievalService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.Map;

/** SSE 使用框架编码事件和 JSON；done 携带校验后的最终回答。 */
@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {
    private final RetrievalService retrievalService;
    private final AnswerService answerService;

    public ChatStreamController(RetrievalService retrievalService, AnswerService answerService) {
        this.retrievalService = retrievalService;
        this.answerService = answerService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> stream(@RequestBody Map<String, String> body) {
        return streamAnswer(body.get("question"));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> streamGet(@RequestParam String question) {
        return streamAnswer(question);
    }

    private Flux<ServerSentEvent<?>> streamAnswer(String rawQuestion) {
        String question = rawQuestion == null ? "" : rawQuestion.strip();
        if (question.isEmpty() || question.length() > 4000) {
            throw new IllegalArgumentException("问题需为 1 到 4000 个字符");
        }
        var retrieval = retrievalService.retrieve(question);
        if (!retrieval.confident() || retrieval.hits().isEmpty()) {
            return Flux.just(event("done", new AnswerService.AnswerResult(question,
                    "抱歉，知识库中未找到相关内容，请换一种问法或上传相关文档。", java.util.List.of(), true)));
        }
        return Flux.defer(() -> {
            StringBuilder response = new StringBuilder();
            Flux<ServerSentEvent<?>> deltas = answerService.streamAnswer(question, retrieval.hits())
                    .map(chunk -> {
                        response.append(chunk);
                        return event("delta", Map.of("text", chunk));
                    });
            return Flux.concat(
                    Flux.just(event("meta", Map.of("question", question))),
                    deltas,
                    Flux.defer(() -> Flux.just(event("done",
                            answerService.validateAnswer(question, response.toString(), retrieval.hits())))));
        }).onErrorResume(error -> Flux.just(event("error", Map.of("message", "生成中断，请稍后重试"))));
    }

    private static ServerSentEvent<?> event(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }
}
