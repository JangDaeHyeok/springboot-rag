package com.jdh.rag.adapter;

import com.jdh.rag.domain.ChannelStat;
import com.jdh.rag.domain.DailyRequestCount;
import com.jdh.rag.domain.ScoreBucket;
import com.jdh.rag.domain.SearchQualityReport;
import com.jdh.rag.port.SearchAnalyticsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * PostgreSQL 기반 검색 품질 분석 어댑터.
 * search_logs 테이블을 집계하여 cosine threshold 튜닝과 파이프라인 모니터링 지표를 제공한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "postgres")
public class PgSearchAnalyticsAdapter implements SearchAnalyticsPort {

    private final SearchLogJpaRepository searchLogJpaRepository;

    @Override
    public SearchQualityReport getReport(Instant from, Instant to) {
        long totalRequests        = searchLogJpaRepository.countDistinctRequests(from, to);
        long feedbackCount        = searchLogJpaRepository.countFeedbackRequests(from, to);
        Double acceptanceRate     = searchLogJpaRepository.findOverallAcceptanceRate(from, to);

        List<ScoreBucket> buckets = searchLogJpaRepository.findScoreBuckets(from, to)
                .stream().map(this::toScoreBucket).toList();

        List<ChannelStat> channels = searchLogJpaRepository.findChannelStats(from, to)
                .stream().map(this::toChannelStat).toList();

        List<DailyRequestCount> daily = searchLogJpaRepository.findDailyRequestCounts(from, to)
                .stream().map(this::toDailyCount).toList();

        return new SearchQualityReport(totalRequests, feedbackCount, acceptanceRate, buckets, channels, daily);
    }

    private ScoreBucket toScoreBucket(Object[] row) {
        double low = row[0] instanceof Number n ? n.doubleValue() : 0.0;
        return new ScoreBucket(low, low + 0.1, toLong(row[1]), toDouble(row[2]));
    }

    private ChannelStat toChannelStat(Object[] row) {
        return new ChannelStat(str(row[0]), toLong(row[1]), toDouble(row[2]));
    }

    private DailyRequestCount toDailyCount(Object[] row) {
        LocalDate date = row[0] instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(str(row[0]));
        return new DailyRequestCount(date, toLong(row[1]));
    }

    private long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private Double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}