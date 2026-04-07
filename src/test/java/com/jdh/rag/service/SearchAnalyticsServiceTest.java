package com.jdh.rag.service;

import com.jdh.rag.domain.SearchQualityReport;
import com.jdh.rag.port.SearchAnalyticsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchAnalyticsServiceTest {

    @Mock  private SearchAnalyticsPort   searchAnalyticsPort;
    @InjectMocks private SearchAnalyticsService service;

    // ── 기간 지정 케이스 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("from, to를 모두 지정하면 그대로 포트에 전달한다")
    void from_to_지정이면_그대로_포트에_전달한다() {
        // given
        Instant from = Instant.parse("2026-04-01T00:00:00Z");
        Instant to   = Instant.parse("2026-04-07T23:59:59Z");
        when(searchAnalyticsPort.getReport(from, to)).thenReturn(emptyReport());

        // when
        service.getReport(from, to);

        // then
        verify(searchAnalyticsPort).getReport(from, to);
    }

    @Test
    @DisplayName("from이 null이면 to에서 7일 전으로 대체된다")
    void from이_null이면_to에서_7일_전으로_대체된다() {
        // given
        Instant to = Instant.parse("2026-04-07T12:00:00Z");
        when(searchAnalyticsPort.getReport(any(), any())).thenReturn(emptyReport());

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor   = ArgumentCaptor.forClass(Instant.class);

        // when
        service.getReport(null, to);

        // then
        verify(searchAnalyticsPort).getReport(fromCaptor.capture(), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(to);
        assertThat(fromCaptor.getValue()).isEqualTo(to.minus(7, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("to가 null이면 현재 시각으로 대체된다")
    void to가_null이면_현재_시각으로_대체된다() {
        // given
        Instant before = Instant.now();
        when(searchAnalyticsPort.getReport(any(), any())).thenReturn(emptyReport());

        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);

        // when
        service.getReport(null, null);

        // then
        verify(searchAnalyticsPort).getReport(any(), toCaptor.capture());
        Instant after = Instant.now();
        assertThat(toCaptor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("from, to 모두 null이면 최근 7일 범위를 적용한다")
    void from_to_모두_null이면_최근_7일_범위를_적용한다() {
        // given
        when(searchAnalyticsPort.getReport(any(), any())).thenReturn(emptyReport());

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor   = ArgumentCaptor.forClass(Instant.class);

        // when
        service.getReport(null, null);

        // then
        verify(searchAnalyticsPort).getReport(fromCaptor.capture(), toCaptor.capture());
        long diffDays = ChronoUnit.DAYS.between(fromCaptor.getValue(), toCaptor.getValue());
        assertThat(diffDays).isEqualTo(7);
    }

    @Test
    @DisplayName("포트 반환값을 그대로 전달한다")
    void 포트_반환값을_그대로_전달한다() {
        // given
        SearchQualityReport expected = new SearchQualityReport(100, 30, 0.8, List.of(), List.of(), List.of());
        when(searchAnalyticsPort.getReport(any(), any())).thenReturn(expected);

        // when
        SearchQualityReport result = service.getReport(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());

        // then
        assertThat(result).isEqualTo(expected);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private SearchQualityReport emptyReport() {
        return new SearchQualityReport(0, 0, null, List.of(), List.of(), List.of());
    }
}