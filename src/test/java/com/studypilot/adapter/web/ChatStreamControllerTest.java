package com.studypilot.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.application.answer.AnswerService;
import com.studypilot.application.retrieve.*;
import com.studypilot.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatStreamControllerTest {
    @Test void servletSerializesMultilineJsonAndValidatedCompletion() throws Exception {
        var retrieval = mock(RetrievalService.class);
        var chat = mock(org.springframework.ai.chat.client.ChatClient.class);
        var answers = spy(new AnswerService(retrieval, chat));
        var hits = List.of(new RetrievalHit(TextChunk.of(1L, "a\t\".md", "标题", 0, "正文"), .9, .9, 0, 1, 0));
        when(retrieval.retrieve("test")).thenReturn(new RetrievalResult("test", hits, true));
        doReturn(Flux.just("第一行\n", "{JSON} [1]")).when(answers).streamAnswer("test", hits);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChatStreamController(retrieval, answers))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var pending = mvc.perform(post("/api/chat/stream").contentType("application/json")
                .content("{\"question\":\"test\"}")).andExpect(request().asyncStarted()).andReturn();
        String body = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("event:delta"), body);
        assertTrue(body.contains("event:done"), body);
        var mapper = new ObjectMapper();
        var events = body.split("\n\n");
        var delta = mapper.readTree(events[1].substring(events[1].indexOf("data:") + 5));
        assertEquals("第一行\n", delta.get("text").asText());
        var done = mapper.readTree(events[events.length - 1].substring(events[events.length - 1].indexOf("data:") + 5));
        assertEquals("第一行\n{JSON} [1]", done.get("answer").asText());
        assertFalse(done.get("rejected").asBoolean());
    }

    @Test void nullAndOverlongQuestionsAreBadRequests() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ChatController(mock(AnswerService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/chat").contentType("application/json").content("{\"question\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").contentType("application/json")
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("question", "x".repeat(4001)))))
                .andExpect(status().isBadRequest());
    }
}
