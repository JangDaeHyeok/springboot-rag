package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.RerankPort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RRF 점수 기준 리랭킹 어댑터.
 * RRF 결합 후 이미 점수 내림차순으로 정렬된 결과를 topK 개수만큼 반환한다.
 */
@Component
public class ScoreRerankAdapter implements RerankPort {

    @Override
    public List<SearchHit> rerank(String query, List<SearchHit> candidates, int topK, boolean sortByLatest) {
        if (candidates == null) return List.of();
        return candidates.stream().limit(topK).toList();
    }
}
