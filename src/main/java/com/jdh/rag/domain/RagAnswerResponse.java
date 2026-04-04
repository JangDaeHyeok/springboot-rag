package com.jdh.rag.domain;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/** RAG 최종 응답: 답변 + 출처 목록 */
public record RagAnswerResponse(
        String requestId,
        String answer,
        List<Citation> citations
) {

    /**
     * 답변 근거 출처.
     * 사용자에게 "어떤 문서에서 이 정보를 가져왔는지" 반환할 때 쓴다.
     */
    @Builder
    public record Citation(
            String citeKey,                  // [S1], [S2] 등 프롬프트 내 인용 키
            String docId,
            String chunkId,
            String source,                   // 파일명, URL 등
            String snippet,                  // content 앞부분 발췌
            Map<String, Object> meta
    ) {}
}
