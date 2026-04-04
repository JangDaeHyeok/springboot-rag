package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.RerankPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * sortByLatest 플래그에 따라 구체 어댑터에 위임하는 디스패처.
 *
 * - sortByLatest=false (기본값): RRF 점수 순 유지 (ScoreRerankAdapter)
 * - sortByLatest=true : 문서 등록일자 내림차순 재정렬 (DateDescRerankAdapter)
 *
 * @Primary로 등록되어 HybridSearchService에 주입된다.
 */
@Primary
@Component
@RequiredArgsConstructor
public class RerankDispatcher implements RerankPort {

    private final ScoreRerankAdapter    scoreAdapter;
    private final DateDescRerankAdapter dateDescAdapter;

    @Override
    public List<SearchHit> rerank(String query, List<SearchHit> candidates, int topK, boolean sortByLatest) {
        if (sortByLatest) {
            return dateDescAdapter.rerank(query, candidates, topK, true);
        }
        return scoreAdapter.rerank(query, candidates, topK, false);
    }
}
