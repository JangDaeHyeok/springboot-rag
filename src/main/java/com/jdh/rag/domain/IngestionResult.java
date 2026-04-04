package com.jdh.rag.domain;

/** 문서 수집 결과 */
public record IngestionResult(
        String docId,
        int chunkCount    // 생성된 chunk 수
) {}