package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.RerankPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 문서 등록일자(meta.createdAt) 내림차순 리랭킹 어댑터.
 * RRF 결합 결과를 createdAt 기준으로 재정렬해 최신 문서를 상위에 노출한다.
 * createdAt은 IngestionService가 Instant.now().toString()으로 저장한 ISO-8601 문자열이다.
 * createdAt이 없는 문서는 Instant.EPOCH로 처리해 하위에 배치한다.
 */
@Slf4j
@Component
public class DateDescRerankAdapter implements RerankPort {

    @Override
    public List<SearchHit> rerank(String query, List<SearchHit> candidates, int topK, boolean sortByLatest) {
        if (candidates == null) return List.of();
        return candidates.stream()
                .sorted(Comparator.comparing(this::extractCreatedAt).reversed())
                .limit(topK)
                .toList();
    }

    private Instant extractCreatedAt(SearchHit hit) {
        if (hit.meta() == null) return Instant.EPOCH;
        Object val = hit.meta().get("createdAt");
        if (val == null) return Instant.EPOCH;
        try {
            return Instant.parse(val.toString());
        } catch (Exception e) {
            log.debug("createdAt 파싱 실패: chunkId={}, value={}", hit.id(), val);
            return Instant.EPOCH;
        }
    }
}
