package com.studypilot.application.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 启动自检：未配置 DashScope API Key 时给出醒目提示（不阻断启动，便于先看页面）。 */
@Component
@Order(1)
public class ApiKeyCheckRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyCheckRunner.class);

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Override
    public void run(ApplicationArguments args) {
        if (apiKey == null || apiKey.isBlank() || "mock".equalsIgnoreCase(apiKey)) {
            log.warn("""

                    ============================================================
                     未检测到有效的 DASHSCOPE_API_KEY！
                     页面可正常打开，但 入库/问答/评测 需要真实 Key 才能工作。
                     请设置环境变量后重启：
                       PowerShell : $env:DASHSCOPE_API_KEY="sk-xxx"
                       CMD        : set DASHSCOPE_API_KEY=sk-xxx
                     申请地址：阿里云百炼平台（DashScope，含免费额度）
                    ============================================================
                    """);
        } else {
            log.info("DashScope API Key 已配置，功能完整可用。");
        }
    }
}
