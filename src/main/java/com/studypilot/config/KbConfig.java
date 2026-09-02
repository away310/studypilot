package com.studypilot.config;

import com.studypilot.infrastructure.splitter.StructureAwareSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KbConfig {

    @Value("${app.kb.chunk-max-chars:800}")
    private int maxChunkChars;

    @Value("${app.kb.chunk-overlap-chars:100}")
    private int overlapChars;

    @Bean
    public StructureAwareSplitter structureAwareSplitter() {
        return new StructureAwareSplitter(maxChunkChars, overlapChars);
    }
}
