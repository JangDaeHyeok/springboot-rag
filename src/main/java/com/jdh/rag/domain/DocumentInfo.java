package com.jdh.rag.domain;

import java.time.Instant;

/**
 * 수집된 문서의 요약 정보.
 * 문서 목록 조회 API 응답에 사용한다.
 */
public record DocumentInfo(
        String docId,
        String source,
        String domain,
        String version,
        String tenantId,
        long chunkCount,
        Instant firstCreatedAt
) {}
