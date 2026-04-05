package com.jdh.rag.domain;

import lombok.Builder;

import java.util.Map;
import java.util.Objects;

/**
 * 하이브리드 검색 요청.
 * keyword(BM25)·vector 각각 topN 검색 후 RRF 합산 → topKFinal 반환.
 * keywordQuery/vectorQuery는 QueryPreprocessPort가 채널별로 최적화한 결과.
 * 원문 query는 리랭킹·로깅용으로만 사용.
 */
@Builder
public record HybridSearchRequest(
        String query,          // 원문 사용자 질의 (리랭킹·로깅용)
        String keywordQuery,   // BM25용 전처리 쿼리 (핵심 명사·동사 중심)
        String vectorQuery,    // Vector용 전처리 쿼리 (HyDE 가상 답변)
        int topNKeyword,
        int topNVector,
        int topKFinal,
        Double vectorThreshold,
        Map<String, Object> filters,
        boolean sortByLatest   // true면 RRF 이후 createdAt 내림차순 재정렬
) {
    public HybridSearchRequest {
        Objects.requireNonNull(query,        "query는 필수입니다.");
        Objects.requireNonNull(keywordQuery, "keywordQuery는 필수입니다.");
        Objects.requireNonNull(vectorQuery,  "vectorQuery는 필수입니다.");
    }
}
