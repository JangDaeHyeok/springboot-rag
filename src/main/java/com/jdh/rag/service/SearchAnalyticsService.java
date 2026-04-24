package com.jdh.rag.service;

import com.jdh.rag.domain.SearchLogPoint;
import com.jdh.rag.domain.SearchQualityReport;
import com.jdh.rag.port.SearchAnalyticsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 검색 품질 분석 서비스.
 * from/to 기간을 기준으로 search_logs 를 집계해 cosine threshold 튜닝 지표를 제공한다.
 */
@Service
@RequiredArgsConstructor
public class SearchAnalyticsService {

    private static final int DEFAULT_DAYS = 7;
    private static final int DEFAULT_SCATTER_LIMIT = 5000;

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

    /**
     * @param from  조회 시작 시각 (null이면 최근 7일)
     * @param to    조회 종료 시각 (null이면 현재)
     * @param limit 최대 건수 (null이면 5000)
     */
    public List<SearchLogPoint> getScatterPoints(Instant from, Instant to, Integer limit) {
        Instant resolvedTo   = to   != null ? to   : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(DEFAULT_DAYS, ChronoUnit.DAYS);
        int resolvedLimit    = limit != null && limit > 0 ? limit : DEFAULT_SCATTER_LIMIT;
        return searchAnalyticsPort.getScatterPoints(resolvedFrom, resolvedTo, resolvedLimit);
    }
}
