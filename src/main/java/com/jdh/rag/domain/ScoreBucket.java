package com.jdh.rag.domain;

/**
 * cosine threshold 튜닝용 점수 구간 통계.
 * scoreLow~scoreHigh 구간의 총 건수와 답변 수락률을 나타낸다.
 */
public record ScoreBucket(
        double scoreLow,
        double scoreHigh,
        long total,
        Double acceptanceRate   // 피드백이 없으면 null
) {}
