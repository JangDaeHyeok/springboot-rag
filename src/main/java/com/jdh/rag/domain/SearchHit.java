package com.jdh.rag.domain;

import java.util.Map;

/**
 * 검색 결과 단위 (chunk 기준).
 * lexical(BM25), vector(cosine), fused(RRF) 모두 이 타입으로 통일.
 */
public record SearchHit(
        String id,                   // chunkId
        String docId,                // 원문 문서 ID
        String content,              // chunk 본문
        Map<String, Object> meta,    // docId / source / domain / version / tenantId 등
        Double score,                // 원점수 (디버깅/로깅용)
        String channel               // "lexical" | "vector" | "fused"
) {

    /** score 없이 생성하는 편의 팩토리 */
    public static SearchHit of(String id, String docId, String content,
                                Map<String, Object> meta, String channel) {
        return new SearchHit(id, docId, content, meta, null, channel);
    }

    public SearchHit withScore(double newScore) {
        return new SearchHit(id, docId, content, meta, newScore, channel);
    }

    public SearchHit withChannel(String newChannel) {
        return new SearchHit(id, docId, content, meta, score, newChannel);
    }
}