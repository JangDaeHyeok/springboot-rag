package com.jdh.rag.domain;

import lombok.Builder;

import java.time.Instant;

/**
 * 검색 로그 단위 레코드.
 * cosine threshold 튜닝을 위해 반드시 적재해야 하는 최소 스키마.
 *
 * <pre>
 * SELECT
 *   FLOOR(cosine_score * 10) / 10 AS bucket,
 *   COUNT(*) AS total,
 *   SUM(CASE WHEN answer_accepted = false THEN 1 ELSE 0 END)::DECIMAL / COUNT(*) AS failure_rate
 * FROM search_log
 * GROUP BY bucket
 * ORDER BY bucket;
 * </pre>
 */
@Builder
public record SearchLog(
        String requestId,
        String query,
        String docId,
        String chunkId,
        Double cosineScore,        // 벡터 DB에서 나온 원점수
        int rank,                  // topN 내 순위
        boolean usedInPrompt,      // topK에 포함됐는지
        Boolean answerAccepted,    // 사용자 피드백 (null=미수집)
        String channel,            // "lexical" | "vector"
        Instant createdAt
) {}