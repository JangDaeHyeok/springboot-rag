package com.jdh.rag.controller;

import com.jdh.rag.domain.SearchLogPoint;
import com.jdh.rag.domain.SearchQualityReport;
import com.jdh.rag.service.SearchAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SearchAnalyticsService searchAnalyticsService;

    // ── /api/analytics/search ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/analytics/search: 리포트를 정상 반환한다")
    void 검색_리포트를_정상_반환한다() throws Exception {
        // given
        var report = new SearchQualityReport(50, 10, 0.75, List.of(), List.of(), List.of());
        when(searchAnalyticsService.getReport(any(), any())).thenReturn(report);

        // when & then
        mockMvc.perform(get("/api/analytics/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(50))
                .andExpect(jsonPath("$.overallAcceptanceRate").value(0.75));
    }

    // ── /api/analytics/scatter ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/analytics/scatter: 산점도 데이터를 정상 반환한다")
    void 산점도_데이터를_정상_반환한다() throws Exception {
        // given
        var point = new SearchLogPoint(
                "req-1", "doc-1", "chunk-1", 0.85, 1, true, true, "vector",
                Instant.parse("2026-04-20T10:00:00Z")
        );
        when(searchAnalyticsService.getScatterPoints(isNull(), isNull(), isNull()))
                .thenReturn(List.of(point));

        // when & then
        mockMvc.perform(get("/api/analytics/scatter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value("req-1"))
                .andExpect(jsonPath("$[0].cosineScore").value(0.85))
                .andExpect(jsonPath("$[0].channel").value("vector"))
                .andExpect(jsonPath("$[0].usedInPrompt").value(true));
    }

    @Test
    @DisplayName("GET /api/analytics/scatter: 데이터가 없으면 빈 배열을 반환한다")
    void 산점도_데이터가_없으면_빈_배열을_반환한다() throws Exception {
        // given
        when(searchAnalyticsService.getScatterPoints(isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/analytics/scatter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/analytics/scatter: limit 파라미터를 전달한다")
    void 산점도_limit_파라미터를_전달한다() throws Exception {
        // given
        when(searchAnalyticsService.getScatterPoints(any(), any(), any()))
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/analytics/scatter").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
