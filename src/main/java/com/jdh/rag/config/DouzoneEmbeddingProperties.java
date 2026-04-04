package com.jdh.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Douzone 커스텀 임베딩 API 설정.
 * rag.embedding.type=douzone 일 때 활성화.
 */
@ConfigurationProperties(prefix = "rag.embedding.douzone")
public record DouzoneEmbeddingProperties(

        @DefaultValue("https://private-ai.douzone.com/ailab-embedding-8")
        String url,

        /**
         * 임베딩 벡터 차원 수.
         * 실제 모델 차원에 맞게 설정해야 VectorStore가 올바르게 동작한다.
         * ailab-embedding-8 모델의 실제 차원은 API 호출로 확인 필요.
         */
        @DefaultValue("1024")
        int dimensions,

        /**
         * 응답 JSON에서 벡터 배열을 추출할 필드 이름.
         * - "embedding"  → { "embedding": [...] }
         * - "vector"     → { "vector": [...] }
         * - ""(빈 문자열) → 응답 자체가 배열 [...]
         */
        @DefaultValue("embedding")
        String responseField,

        /** HTTP 연결 타임아웃 (ms) */
        @DefaultValue("5000")
        int connectTimeout,

        /** HTTP 읽기 타임아웃 (ms) */
        @DefaultValue("30000")
        int readTimeout
) {}
