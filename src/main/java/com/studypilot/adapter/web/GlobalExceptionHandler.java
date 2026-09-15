package com.studypilot.adapter.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理：把 LLM/embedding 调用失败转成带文案的 503，
 * 避免前端拿到裸 500 与无意义堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        String message = e instanceof IllegalArgumentException ? e.getMessage() : "请求格式或参数不正确";
        return ResponseEntity.badRequest().body(Map.of("error", message == null ? "请求参数不正确" : message));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(Exception e) {
        return ResponseEntity.status(413).body(Map.of("error", "文件超过上传大小限制"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        log.warn("请求处理失败: {}", msg);

        HttpStatus status;
        String friendly;
        if (msg != null && (msg.contains("401") || msg.contains("apiKey") || msg.contains("ApiKey"))) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            friendly = "DashScope API Key 无效或未配置，请检查环境变量 DASHSCOPE_API_KEY";
        } else if (msg != null && (msg.contains("429") || msg.contains("rate limit"))) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            friendly = "DashScope 调用频率超限（429），请稍后重试";
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            friendly = "服务暂时不可用，请稍后重试";
        }
        return ResponseEntity.status(status).body(Map.of("error", friendly));
    }
}
