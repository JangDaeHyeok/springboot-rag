package com.jdh.rag.support;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.domain.SearchHit;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * Reciprocal Rank Fusion (RRF) 구현.
 *
 * <pre>
 * score(doc) = Σ  1 / (k + rank)
 * </pre>
 *
 * k=60 은 Elasticsearch 공식 기본값이자 실무 출발점.
 * 점수 스케일이 다른 BM25 / cosine 결과를 정규화 없이 합칠 수 있다.
 */
@Component
public class RrfRankFusion {

    private final int k;

    public RrfRankFusion(RagProperties ragProperties) {
        this.k = ragProperties.rrfK();
    }

    /**
     * 여러 랭킹 목록을 RRF로 합쳐 topK를 반환한다.
     *
     * @param rankedLists id 함수로 식별 가능한 랭킹 목록들 (순서대로 rank 계산)
     * @param idFn        SearchHit → 유일 식별자 (chunkId 권장)
     * @param topK        반환할 최대 수
     * @return RRF 점수 내림차순, 최대 topK
     */
    public List<SearchHit> fuse(List<List<SearchHit>> rankedLists,
                                 Function<SearchHit, String> idFn,
                                 int topK) {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, SearchHit>  itemMap = new LinkedHashMap<>();

        for (List<SearchHit> ranked : rankedLists) {
            if (ranked == null) continue;
            for (int i = 0; i < ranked.size(); i++) {
                SearchHit hit = ranked.get(i);
                String id = idFn.apply(hit);
                itemMap.putIfAbsent(id, hit);
                double rrfScore = 1.0 / (k + (i + 1));
                scoreMap.merge(id, rrfScore, Double::sum);
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> itemMap.get(e.getKey()).withScore(scoreMap.get(e.getKey())).withChannel("fused"))
                .toList();
    }

    /** 두 목록만 합치는 편의 메서드 */
    public List<SearchHit> fuse(List<SearchHit> listA, List<SearchHit> listB,
                                 Function<SearchHit, String> idFn, int topK) {
        return fuse(List.of(
                listA != null ? listA : List.of(),
                listB != null ? listB : List.of()
        ), idFn, topK);
    }
}