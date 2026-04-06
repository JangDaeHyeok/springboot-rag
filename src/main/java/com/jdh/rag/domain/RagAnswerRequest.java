package com.jdh.rag.domain;

import java.util.Map;

/**
 * RAG 답변 생성 요청 도메인 객체.
 * RagAnswerService 의 단일 진입점으로 사용한다.
 */
public record RagAnswerRequest(
        String query,
        Map<String, Object> filters,
        boolean sortByLatest
) {
    public RagAnswerRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("질의(query)는 필수입니다.");
        }
    }

    public RagAnswerRequest(String query, Map<String, Object> filters) {
        this(query, filters, false);
    }
}
