package com.jdh.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * rag.* 설정 바인딩.
 * application.properties / application.yml 에서 커스터마이즈 가능.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @DefaultValue("postgres") String keywordSearchType,
        @DefaultValue("60")       int    rrfK,
        @DefaultValue("50")       int    topNKeyword,
        @DefaultValue("50")       int    topNVector,
        @DefaultValue("5")        int    topKFinal,
        @DefaultValue("0.6")      double vectorThreshold,
        @DefaultValue("1200")     int    maxCharsPerChunk,
        ChunkProperties chunk
) {

    public record ChunkProperties(
            @DefaultValue("600") int size,
            @DefaultValue("100") int overlap,
            @DefaultValue("semantic") String strategy
    ) {}
}
