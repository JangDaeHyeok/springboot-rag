package com.jdh.rag.controller;

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
     *
     * 리포트 항목:
     *   - totalRequests        : 기간 내 총 요청 건수
     *   - feedbackCount        : 피드백이 수집된 요청 건수
     *   - overallAcceptanceRate: 전체 답변 수락률 (피드백 없으면 null)
     *   - scoreBuckets         : 점수 구간별 건수 + 수락률 (cosine threshold 튜닝용)
     *   - channelStats         : 검색 채널별 건수 + 평균 점수
     *   - dailyStats           : 일별 요청 건수 추이
     */
    @GetMapping("/search")
    public ResponseEntity<SearchQualityReport> getSearchReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(searchAnalyticsService.getReport(from, to));
    }
}
