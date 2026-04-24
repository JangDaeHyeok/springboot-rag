package com.jdh.rag.port;

import com.jdh.rag.domain.SearchLogPoint;
import com.jdh.rag.domain.SearchQualityReport;

import java.time.Instant;
import java.util.List;

/**
 * 검색 품질 분석 포트.
 * search_logs 를 집계해 cosine threshold 튜닝 및 파이프라인 모니터링에 필요한 지표를 제공한다.
 */
public interface SearchAnalyticsPort {

    SearchQualityReport getReport(Instant from, Instant to);

    List<SearchLogPoint> getScatterPoints(Instant from, Instant to, int limit);
}
