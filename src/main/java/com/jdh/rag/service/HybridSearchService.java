package com.jdh.rag.service;

import com.jdh.rag.domain.HybridSearchRequest;
import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.KeywordSearchPort;
import com.jdh.rag.port.RerankPort;
import com.jdh.rag.port.VectorSearchPort;
import com.jdh.rag.support.RrfRankFusion;
import com.jdh.rag.support.SearchLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/**
 * 하이브리드 검색 파이프라인:
 * BM25 keyword → Vector → RRF 결합 → 리랭킹(sortByLatest) → 검색 로그 적재
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final KeywordSearchPort keywordSearchPort;
    private final VectorSearchPort  vectorSearchPort;
    private final RerankPort        rerankPort;
    private final RrfRankFusion     rrfRankFusion;
    private final SearchLogger      searchLogger;

    public List<SearchHit> search(HybridSearchRequest request, String requestId) {
        // 1) 각각 topN 검색 (실패 시 빈 목록으로 degrade)
        List<SearchHit> lexical = safeSearch(() ->
                keywordSearchPort.search(request.query(), request.topNKeyword(), request.filters()),
                "keyword"
        );

        List<SearchHit> vector = safeSearch(() ->
                vectorSearchPort.search(request.query(), request.topNVector(),
                        request.vectorThreshold(), request.filters()),
                "vector"
        );

        log.debug("하이브리드 검색: lexical={}건, vector={}건", lexical.size(), vector.size());

        // 2) RRF 결합
        List<SearchHit> fused = rrfRankFusion.fuse(lexical, vector, SearchHit::id,
                request.topKFinal() * 3   // 리랭킹 입력 후보를 넉넉히 확보
        );

        // 3) 리랭킹 → topKFinal
        List<SearchHit> ranked = rerankPort.rerank(request.query(), fused, request.topKFinal(), request.sortByLatest());

        // 4) 검색 로그 (cosine threshold 튜닝용)
        searchLogger.logBatch(requestId, request.query(), fused, ranked);

        return ranked;
    }

    private List<SearchHit> safeSearch(Supplier<List<SearchHit>> supplier,
                                        String channel) {
        try {
            List<SearchHit> result = supplier.get();
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.error("[{}] 검색 실패: {}", channel, e.getMessage());
            return List.of();
        }
    }
}