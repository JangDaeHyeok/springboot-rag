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
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Supplier;

/**
 * 하이브리드 검색 파이프라인: BM25 + Vector 병렬 실행(StructuredTaskScope) → RRF → 리랭킹 → 로그 적재.
 * I/O 바운드 두 검색을 virtual thread로 병렬화해 지연 시간을 max(bm25, vector)로 단축.
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
        // 1) BM25 + Vector 병렬 검색 (각각 실패 시 빈 목록으로 degrade)
        List<SearchHit> lexical;
        List<SearchHit> vector;

        try (var scope = StructuredTaskScope.open()) {
            var lexicalTask = scope.fork(() ->
                    safeSearch(() ->
                            keywordSearchPort.search(request.keywordQuery(), request.topNKeyword(), request.filters()),
                            "keyword"));
            var vectorTask = scope.fork(() ->
                    safeSearch(() ->
                            vectorSearchPort.search(request.vectorQuery(), request.topNVector(),
                                    request.vectorThreshold(), request.filters()),
                            "vector"));

            scope.join();   // 두 subtask 모두 완료될 때까지 대기
            // safeSearch가 예외를 내부에서 흡수하므로 subtask는 항상 정상 완료
            lexical = lexicalTask.get();
            vector  = vectorTask.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("병렬 검색 인터럽트 (빈 결과 반환): {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("병렬 검색 실패 (빈 결과 반환): {}", e.getMessage());
            return List.of();
        }

        log.info("하이브리드 검색: lexical={}건, vector={}건", lexical.size(), vector.size());

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