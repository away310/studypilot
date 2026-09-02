package com.studypilot.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI 接入阿里云 DashScope（通义千问）。
 *
 * 两条通道（与实习项目 TechPilot 相同的已验证坐标）：
 * 1. Chat：DashScope 兼容 OpenAI 协议 → spring-ai-openai ChatClient
 * 2. Embedding：DashScope 原生 DashScopeEmbeddingModel（text-embedding-v3）
 *
 * API Key 一律从环境变量读取，不落盘、不进仓库。
 */
@Configuration
public class DashScopeConfig {

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.model:qwen-plus}")
    private String chatModel;

    @Value("${spring.ai.openai.chat.temperature:0.3}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.max-tokens:2048}")
    private Integer maxTokens;

    @Value("${spring.ai.openai.embedding.model:text-embedding-v3}")
    private String embeddingModel;

    /** 主对话模型：OpenAI 兼容协议指向 DashScope。 */
    @Bean
    @Primary
    public OpenAiChatModel dashScopeChatModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .apiKey(apiKey)
                .completionsPath("/chat/completions")
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(chatModel)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(@Qualifier("dashScopeChatModel") OpenAiChatModel model) {
        return ChatClient.builder(model);
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /** 向量化模型：DashScope 原生 SDK。 */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .baseUrl("https://dashscope.aliyuncs.com")
                .build();
    }

    @Bean
    public EmbeddingModel dashScopeEmbeddingModel(DashScopeApi api) {
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(embeddingModel)
                .build();
        return new DashScopeEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return null;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
