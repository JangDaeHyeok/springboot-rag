package com.jdh.rag.domain;

import lombok.Builder;

import java.util.Map;

/**
 * 하이브리드 검색 요청.
 * keyword(BM25)와 vector 각각 topN을 검색 후 RRF로 합쳐 topKFinal 반환.
 */
@Builder
public record HybridSearchRequest(
        String query,
        int topNKeyword,
        int topNVector,
        int topKFinal,
        Double vectorThreshold,
        Map<String, Object> filters,
        boolean sortByLatest     // true면 RRF 이후 createdAt 내림차순 재정렬
) {}
