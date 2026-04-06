package com.jdh.rag.domain;

import java.time.LocalDate;

/**
 * 일별 RAG 요청 건수.
 */
public record DailyRequestCount(
        LocalDate date,
        long requestCount
) {}
