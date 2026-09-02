package com.studypilot.adapter.web;

import com.studypilot.application.eval.EvalReport;
import com.studypilot.application.eval.EvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /** 跑内置评测集三组对比（向量 / 关键词 / 混合），返回 Recall@5 报告。 */
    @GetMapping("/run")
    public List<EvalReport> run() throws IOException {
        return evalService.runDefaultEval();
    }
}
