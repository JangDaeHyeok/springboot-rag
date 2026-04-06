package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchQualityReport;
import com.jdh.rag.port.SearchAnalyticsPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 인메모리 모드용 검색 분석 어댑터 (No-Op).
 * 인메모리 모드에서는 search_logs 가 DB에 적재되지 않으므로 빈 리포트를 반환한다.
 */
@Component
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "memory", matchIfMissing = true)
public class NoOpSearchAnalyticsAdapter implements SearchAnalyticsPort {

    @Override
    public SearchQualityReport getReport(Instant from, Instant to) {
        return new SearchQualityReport(0, 0, null, List.of(), List.of(), List.of());
    }
}