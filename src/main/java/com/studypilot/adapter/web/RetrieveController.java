package com.studypilot.adapter.web;

import com.studypilot.application.retrieve.RetrievalResult;
import com.studypilot.application.retrieve.RetrievalService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 检索调试接口：查看问题命中哪些块及各通道得分（用于调参与诊断）。 */
@RestController
@RequestMapping("/api/retrieve")
public class RetrieveController {

    private final RetrievalService retrievalService;

    public RetrieveController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping
    public RetrievalResult debug(@RequestParam("q") String question,
                                 @RequestParam(value = "mode", defaultValue = "hybrid") String mode) {
        RetrievalService.Mode m = switch (mode.toLowerCase()) {
            case "vector" -> RetrievalService.Mode.VECTOR;
            case "keyword" -> RetrievalService.Mode.KEYWORD;
            default -> RetrievalService.Mode.HYBRID;
        };
        return retrievalService.retrieve(question, m);
    }

    /** 供页面展示单次检索命中。 */
    @PostMapping
    public RetrievalResult debugPost(@RequestBody Map<String, String> body) {
        return retrievalService.retrieve(body.getOrDefault("question", ""), RetrievalService.Mode.HYBRID);
    }
}
