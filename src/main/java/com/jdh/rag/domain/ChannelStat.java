package com.jdh.rag.domain;

/**
 * 검색 채널(lexical / vector / fused)별 통계.
 */
public record ChannelStat(
        String channel,
        long count,
        Double avgScore
) {}