package com.jdh.rag.port;

import com.jdh.rag.domain.SearchHit;

import java.util.List;

/**
 * 리랭킹 포트.
 * 구현체: ScoreRerankAdapter, DateDescRerankAdapter
 * 선택자: RerankDispatcher (@Primary)
 */
public interface RerankPort {

    /**
     * @param query        사용자 질의
     * @param candidates   RRF 결합 후 topN 후보 목록
     * @param topK         최종 반환할 수
     * @param sortByLatest true면 문서 등록일자(createdAt) 내림차순 재정렬, false면 RRF 점수 순 유지
     * @return 리랭킹 결과 SearchHit 목록 (최대 topK)
     */
    List<SearchHit> rerank(String query, List<SearchHit> candidates, int topK, boolean sortByLatest);

    /** sortByLatest 생략 시 RRF 점수 순 유지 */
    default List<SearchHit> rerank(String query, List<SearchHit> candidates, int topK) {
        return rerank(query, candidates, topK, false);
    }
}
