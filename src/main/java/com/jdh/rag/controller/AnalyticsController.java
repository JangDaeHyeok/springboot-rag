package com.jdh.rag.controller;

import com.jdh.rag.domain.SearchLogPoint;
import com.jdh.rag.domain.SearchQualityReport;
import com.jdh.rag.service.SearchAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 검색 품질 분석 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final SearchAnalyticsService searchAnalyticsService;

    /**
     * GET /api/analytics/search?from=...&to=...
     *
     * 검색 품질 리포트를 반환한다. from/to 미입력 시 최근 7일 기준.
     */
    @GetMapping("/search")
    public ResponseEntity<SearchQualityReport> getSearchReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(searchAnalyticsService.getReport(from, to));
    }

    /**
     * GET /api/analytics/scatter?from=...&to=...&limit=...
     *
     * 산점도 시각화용 개별 검색 로그 포인트를 반환한다.
     * cosine_score 가 존재하는 행만 최신순으로 limit 건까지 반환한다.
     */
    @GetMapping("/scatter")
    public ResponseEntity<List<SearchLogPoint>> getScatterPoints(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(searchAnalyticsService.getScatterPoints(from, to, limit));
    }
}
