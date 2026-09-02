package com.studypilot.adapter.web;

import com.studypilot.application.answer.AnswerService;
import com.studypilot.application.retrieve.RetrievalResult;
import com.studypilot.application.retrieve.RetrievalService;
import com.studypilot.model.RetrievalHit;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * SSE 流式问答：先发元信息事件（拒答标记 + 引用来源），再逐段推送生成内容。
 * 页面通过 EventSource 消费。
 */
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
    public Flux<String> stream(@RequestBody Map<String, String> body) {
        return streamAnswer(body.getOrDefault("question", "").strip());
    }

    /** EventSource 兼容的 GET 版本（页面使用）。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamGet(@RequestParam("question") String question) {
        return streamAnswer(question == null ? "" : question.strip());
    }

    private Flux<String> streamAnswer(String question) {
        if (question.isEmpty()) {
            return Flux.just("event: error\ndata: 问题不能为空\n\n");
        }

        RetrievalResult retrieval = retrievalService.retrieve(question);
        if (!retrieval.confident() || retrieval.hits().isEmpty()) {
            return Flux.just(
                    "event: meta\ndata: " + jsonMeta(question, null, true) + "\n\n",
                    "event: delta\ndata: 抱歉，我在当前知识库中没有找到与这个问题相关的内容，请换一种问法或先上传相关文档。\n\n"
            );
        }

        // 元信息事件（引用来源）
        Flux<String> meta = Flux.just("event: meta\ndata: " + jsonMeta(question, retrieval.hits(), false) + "\n\n");

        // 流式正文
        Flux<String> deltas = answerService.streamAnswer(question, retrieval.hits())
                .map(chunk -> "event: delta\ndata: " + chunk + "\n\n");

        return Flux.concat(meta, deltas);
    }

    private String jsonMeta(String question, List<RetrievalHit> citations, boolean rejected) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"question\":\"").append(escape(question)).append("\",");
        sb.append("\"rejected\":").append(rejected).append(",");
        sb.append("\"citations\":[");
        if (citations != null) {
            for (int i = 0; i < citations.size(); i++) {
                RetrievalHit h = citations.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"doc\":\"").append(escape(h.chunk().docName()))
                        .append("\",\"section\":\"").append(escape(h.chunk().headingPath() == null ? "" : h.chunk().headingPath()))
                        .append("\",\"score\":").append(String.format("%.3f", h.score()))
                        .append("}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
