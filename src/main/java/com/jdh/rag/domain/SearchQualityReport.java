package com.jdh.rag.domain;

import java.util.List;

/**
 * 검색 품질 리포트.
 * cosine threshold 튜닝과 RAG 파이프라인 성능 모니터링에 사용한다.
 */
public record SearchQualityReport(
        long totalRequests,
        long feedbackCount,
        Double overallAcceptanceRate,   // 피드백이 없으면 null
        List<ScoreBucket> scoreBuckets,
        List<ChannelStat> channelStats,
        List<DailyRequestCount> dailyStats
) {}
