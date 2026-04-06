package com.jdh.rag.service;

import com.jdh.rag.domain.SearchQualityReport;
import com.jdh.rag.port.SearchAnalyticsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 검색 품질 분석 서비스.
 * from/to 기간을 기준으로 search_logs 를 집계해 cosine threshold 튜닝 지표를 제공한다.
 */
@Service
@RequiredArgsConstructor
public class SearchAnalyticsService {

    private static final int DEFAULT_DAYS = 7;

    private final SearchAnalyticsPort searchAnalyticsPort;

    /**
     * @param from  집계 시작 시각 (null이면 최근 7일)
     * @param to    집계 종료 시각 (null이면 현재)
     */
    public SearchQualityReport getReport(Instant from, Instant to) {
        Instant resolvedTo   = to   != null ? to   : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(DEFAULT_DAYS, ChronoUnit.DAYS);
        return searchAnalyticsPort.getReport(resolvedFrom, resolvedTo);
    }
}
