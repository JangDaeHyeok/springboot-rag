package com.jdh.rag.domain;

/**
 * 쿼리 전처리 결과.
 * 원문 쿼리를 검색 채널별로 최적화한 형태로 담는다.
 */
public record ProcessedQuery(
        String keywordQuery,   // BM25용 — 핵심 명사·동사 중심으로 정제된 쿼리
        String vectorQuery     // Vector용 — HyDE 가상 답변 문서 (의미 검색 정확도 향상)
) {}
