package com.jdh.rag.domain;

import java.time.Instant;

/**
 * 산점도 시각화용 검색 로그 개별 데이터 포인트.
 */
public record SearchLogPoint(
        String requestId,
        String docId,
        String chunkId,
        Double cosineScore,
        int rank,
        boolean usedInPrompt,
        Boolean answerAccepted,
        String channel,
        Instant createdAt
) {}
