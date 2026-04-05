package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchLog;
import com.jdh.rag.port.SearchLogPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 개발·테스트 전용 No-Op SearchLogPort.
 * rag.keyword-search-type=memory(기본값 포함)일 때 활성화.
 * InMemoryKeywordSearchAdapter와 동일한 조건을 사용한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "memory", matchIfMissing = true)
public class NoOpSearchLogAdapter implements SearchLogPort {

    @Override
    public void save(SearchLog searchLog) {
        log.info("[NoOp] SearchLog 저장 생략: requestId={}", searchLog.requestId());
    }

    @Override
    public void saveBatch(List<SearchLog> logs) {
        log.info("[NoOp] SearchLog 배치 저장 생략: {}건", logs != null ? logs.size() : 0);
    }
}
