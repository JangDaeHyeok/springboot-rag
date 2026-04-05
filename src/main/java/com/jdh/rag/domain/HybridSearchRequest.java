package com.jdh.rag.domain;

import lombok.Builder;

import java.util.Map;

/**
 * 하이브리드 검색 요청.
 * keyword(BM25)와 vector 각각 topN을 검색 후 RRF로 합쳐 topKFinal 반환.
 *
 * <p>keywordQuery와 vectorQuery는 {@link com.jdh.rag.port.QueryPreprocessPort}가
 * 원문 query를 채널별로 최적화한 결과다. 원문 query는 리랭킹·로깅용으로만 사용한다.
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
) {}
