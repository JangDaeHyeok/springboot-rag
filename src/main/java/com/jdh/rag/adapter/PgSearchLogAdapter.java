package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchLog;
import com.jdh.rag.port.SearchLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * PostgreSQL 기반 검색 로그 적재 어댑터.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "postgres")
public class PgSearchLogAdapter implements SearchLogPort {

    private final SearchLogJpaRepository searchLogJpaRepository;

    @Override
    public void save(SearchLog searchLog) {
        searchLogJpaRepository.save(toEntity(searchLog));
    }

    @Override
    public void saveBatch(List<SearchLog> logs) {
        if (logs == null || logs.isEmpty()) return;
        List<SearchLogEntity> entities = logs.stream().map(this::toEntity).toList();
        searchLogJpaRepository.saveAll(entities);
        log.info("검색 로그 적재 완료: {}건", entities.size());
    }

    private SearchLogEntity toEntity(SearchLog log) {
        return SearchLogEntity.builder()
                .requestId(log.requestId())
                .query(log.query())
                .docId(log.docId())
                .chunkId(log.chunkId())
                .cosineScore(log.cosineScore())
                .rankPos(log.rank())
                .usedInPrompt(log.usedInPrompt())
                .answerAccepted(log.answerAccepted())
                .channel(log.channel())
                .createdAt(log.createdAt() != null ? log.createdAt() : Instant.now())
                .build();
    }
}