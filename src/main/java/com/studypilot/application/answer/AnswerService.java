package com.studypilot.application.answer;

import com.studypilot.application.retrieve.RetrievalResult;
import com.studypilot.application.retrieve.RetrievalService;
import com.studypilot.model.RetrievalHit;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG 问答：检索 → 组 prompt（带引用编号）→ LLM 回答 → 附带引用清单。
 * 检索不达置信度时直接拒答，不调用 LLM（省 token 且防幻觉）。
 * 支持同步返回与 SSE 流式两种方式。
 */
@Service
public class AnswerService {

    private final RetrievalService retrievalService;
    private final ChatClient chatClient;

    public AnswerService(RetrievalService retrievalService, ChatClient chatClient) {
        this.retrievalService = retrievalService;
        this.chatClient = chatClient;
    }

    public AnswerResult answer(String question) {
        RetrievalResult retrieval = retrievalService.retrieve(question);
        if (!retrieval.confident() || retrieval.hits().isEmpty()) {
            return AnswerResult.rejected(question, retrieval);
        }

        String context = buildContext(retrieval.hits());
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        问题：
                        %s

                        参考资料（编号即引用，回答时必须用 [n] 标注依据的编号）：
                        %s
                        """.formatted(question, context))
                .call()
                .content();

        return new AnswerResult(question, response, retrieval.hits(), false);
    }

    /** SSE 流式回答（检索与引用已由调用方确认过，这里只做流式生成）。 */
    public Flux<String> streamAnswer(String question, List<RetrievalHit> hits) {
        String context = buildContext(hits);
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        问题：
                        %s

                        参考资料（编号即引用，回答时必须用 [n] 标注依据的编号）：
                        %s
                        """.formatted(question, context))
                .stream()
                .content();
    }

    private String buildContext(List<RetrievalHit> hits) {
        StringBuilder sb = new StringBuilder();
        AtomicInteger i = new AtomicInteger(1);
        for (RetrievalHit hit : hits) {
            sb.append("[").append(i.getAndIncrement()).append("] ")
                    .append("来源: ").append(hit.chunk().sourceLabel()).append('\n')
                    .append(hit.chunk().content()).append("\n\n");
        }
        return sb.toString();
    }

    public record AnswerResult(
            String question,
            String answer,          // 拒答时为拒答文案
            List<RetrievalHit> citations,
            boolean rejected
    ) {
        static AnswerResult rejected(String question, RetrievalResult retrieval) {
            return new AnswerResult(question,
                    "抱歉，我在当前知识库中没有找到与这个问题相关的内容。你可以换一种问法，或先上传相关文档后再提问。",
                    retrieval.hits(), true);
        }
    }

    private static final String SYSTEM_PROMPT = """
            你是一个严谨的个人知识库问答助手。请遵循：
            1. 只依据"参考资料"作答，不要使用资料之外的知识编造；
            2. 回答中每个关键结论后用 [n] 标注其来源编号（n 为参考资料编号）；
            3. 资料不足以回答时，直接说明"资料中未涉及"，不要强行作答；
            4. 回答使用中文，简洁有条理。
            """;
}
